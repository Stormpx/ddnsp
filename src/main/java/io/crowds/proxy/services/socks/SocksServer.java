package io.crowds.proxy.services.socks;

import io.crowds.Platform;
import io.crowds.proxy.Axis;
import io.crowds.proxy.DatagramOption;
import io.crowds.proxy.common.Socks;
import io.crowds.util.Inet;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.unix.UnixChannelOption;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.handler.codec.socksx.v5.*;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class SocksServer {
    private final static Logger logger= LoggerFactory.getLogger(SocksServer.class);
    private SocksOption socksOption;
    private Axis axis;

    public SocksServer(SocksOption socksOption, Axis axis) {
        this.socksOption = socksOption;
        this.axis = axis;
    }

    public io.vertx.core.Future<Void> start(){
        Promise<Void> promise=Promise.promise();
        InetSocketAddress socketAddress = new InetSocketAddress(socksOption.getHost(), socksOption.getPort());
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        ServerBootstrap bootstrap = serverBootstrap.group(axis.getEventLoopGroup(), axis.getEventLoopGroup()).channel(Platform.getServerSocketChannelClass());
        if (Epoll.isAvailable()){
            bootstrap.option(UnixChannelOption.SO_REUSEPORT,true);
        }
        bootstrap
                .option(ChannelOption.SO_REUSEADDR,true)
                .childOption(ChannelOption.SO_REUSEADDR,true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new SocksPortUnificationServerHandler())
                            .addLast("handler",new Handler());
                    }
                })
                .bind(socketAddress)
                .addListener(future -> {
                if (future.isSuccess()) {
                    promise.complete();
                    logger.info("start socks proxy server {}", socketAddress);
                }else {
                    future.cause().printStackTrace();
                    promise.tryFail(future.cause());
                    logger.error("start socks proxy server failed cause:{}", future.cause().getMessage());
                }
            });

        return promise.future();

    }




    public class Handler extends SimpleChannelInboundHandler<SocksMessage>{

        private Socks5AuthMethod expectAuthMethod;
        private boolean pass=false;

        public Handler() {
            super(false);
        }

        private Socks5PasswordAuthResponse handlePassAuth(Socks5PasswordAuthRequest request){
            if (!Objects.equals(socksOption.getUsername(),request.username())){
                return new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE);
            }
            if (!Objects.equals(socksOption.getPassword(),request.password())){
                return new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE);
            }
            return new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS);
        }

        private void writeMessage(ChannelHandlerContext ctx,Object msg, Consumer<Void> onSuccess){
            ctx.writeAndFlush(msg)
                    .addListener(future -> {
                        if (!future.isSuccess()){
                            future.cause().printStackTrace();
                            logger.warn("write message failed cause:{}",future.cause().getMessage());
                            return;
                        }
                        if (onSuccess!=null)
                            onSuccess.accept(null);
                    });
        }

        private InetSocketAddress getAddress(Socks5CommandRequest request){
            InetSocketAddress dest=null;
            if (request.dstAddrType()==Socks5AddressType.DOMAIN){
                dest= Inet.createSocketAddress(request.dstAddr(),request.dstPort());
            }else{
                dest=new InetSocketAddress(request.dstAddr(),request.dstPort());
            }
            return dest;
        }

        private void releaseChannel(ChannelHandlerContext ctx){
            ChannelPipeline pipeline = ctx.channel().pipeline();
            while (pipeline.removeFirst()!=this){

            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, SocksMessage msg) throws Exception {
            if (!msg.decoderResult().isSuccess()){
                ctx.close();
            }
            if (msg.version()== SocksVersion.SOCKS4a){
                pass=true;
                if (socksOption.isPassAuth()){
                    writeMessage(ctx,new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED),v->ctx.close());
                    return;
                }
                Socks4CommandRequest request= (Socks4CommandRequest) msg;
                writeMessage(ctx,new DefaultSocks4CommandResponse(Socks4CommandStatus.SUCCESS,request.dstAddr(),request.dstPort()),
                        v->{
                            axis.handleTcp(ctx.channel(),ctx.channel().remoteAddress(),new InetSocketAddress(request.dstAddr(),request.dstPort()));
                            releaseChannel(ctx);
                        });
            }else if (msg.version()==SocksVersion.SOCKS5){
                if (msg instanceof Socks5InitialRequest){
                    List<Socks5AuthMethod> authMethods = ((Socks5InitialRequest) msg).authMethods();
                    this.expectAuthMethod=socksOption.isPassAuth()?Socks5AuthMethod.PASSWORD:Socks5AuthMethod.NO_AUTH;
                    var authMethod=authMethods.stream().filter(it->expectAuthMethod.compareTo(it)==0).findFirst();
                    if (authMethod.isEmpty()) {
                        writeMessage(ctx,new DefaultSocks5InitialResponse(Socks5AuthMethod.UNACCEPTED),v->ctx.channel().close());
                        return;
                    }

                    ctx.pipeline().remove(Socks5InitialRequestDecoder.class);
                    if (this.expectAuthMethod.equals(Socks5AuthMethod.PASSWORD)){
                        ctx.pipeline().addBefore(ctx.name(),"decoder",new Socks5PasswordAuthRequestDecoder());
                    }else{
                        pass=true;
                        ctx.pipeline().addBefore(ctx.name(),"decoder",new Socks5CommandRequestDecoder());
                    }
                    ctx.channel().writeAndFlush(new DefaultSocks5InitialResponse(expectAuthMethod));

                }else if (msg instanceof Socks5PasswordAuthRequest){
                    if (!this.expectAuthMethod.equals(Socks5AuthMethod.PASSWORD)){
                        ctx.channel().close();
                        return;
                    }
                    Socks5PasswordAuthResponse authResponse = handlePassAuth((Socks5PasswordAuthRequest) msg);
                    writeMessage(ctx,authResponse,v->{
                        if (authResponse.status()==Socks5PasswordAuthStatus.SUCCESS){
                            pass=true;
                            ctx.pipeline().remove(Socks5PasswordAuthRequestDecoder.class);
                            ctx.pipeline().addBefore(ctx.name(),"decoder",new Socks5CommandRequestDecoder());
                        }else{
                            ctx.channel().close();
                        }
                    });
                }else if (msg instanceof Socks5CommandRequest request){
                    handleCommandRequest(ctx,request);

                }
            }else{
                logger.warn("unknown socks version :{}",msg.version().byteValue());
                ctx.channel().close();
            }
        }

        private InetSocketAddress decodeUdpDstAddr(ByteBuf byteBuf){
            byteBuf.skipBytes(2);
            byte frag = byteBuf.readByte();
            if (frag!=0)
                return null;

            return Socks.decodeAddr(byteBuf);
        }

        private void handleCommandRequest(ChannelHandlerContext ctx,Socks5CommandRequest request){
            if (!pass){
                ctx.channel().close();
                return;
            }
            if (request.type()==Socks5CommandType.UDP_ASSOCIATE){
                //udp
                handleUdpAssociate(ctx);
            }else{
                //tcp
                InetSocketAddress dest = getAddress(request);
                axis.handleTcp(ctx.channel(),ctx.channel().remoteAddress(), dest)
                        .addListener(f->{
                            if (f.isSuccess()){
                                writeMessage(ctx,
                                        new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS,
                                                Socks5AddressType.IPv4, socksOption.getHost(),socksOption.getPort()),
                                        v->releaseChannel(ctx));
                            }
                        });


            }
        }



        private void handleUdpAssociate(ChannelHandlerContext context){
//            InetSocketAddress dest = getAddress(request);
            Future<DatagramChannel> future=axis.getChannelCreator().createDatagramChannel(
                    new DatagramOption().setBindAddr(new InetSocketAddress(socksOption.getHost(), 0)),
                    new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline()
                                    .addLast(new SocksUdpEncoder())
                                    .addLast(new SimpleChannelInboundHandler<DatagramPacket>(false) {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
                                    InetSocketAddress sender = msg.sender();
                                    ByteBuf content = msg.content();
                                    InetSocketAddress dest = decodeUdpDstAddr(content);
                                    if (dest==null){
                                        ReferenceCountUtil.safeRelease(msg);
                                        return;
                                    }

                                    axis.handleUdp0((DatagramChannel) ctx.channel(), new DatagramPacket(content, dest, sender),
                                            fallbackPacket -> {
                                                ctx.channel().writeAndFlush(new DatagramPacket(fallbackPacket.content(),sender,fallbackPacket.sender()));
                                            });
                                }
                            });
                }
            });
            future.addListener(f -> {
                if (!f.isSuccess()){
                    logger.error("{}",f.cause().getMessage());
                    context.close();
                    return;
                }
                DatagramChannel datagramChannel= (DatagramChannel) f.get();
                context.channel().closeFuture().addListener(it -> {
                    datagramChannel.close();
                });
                InetSocketAddress bindAddr = datagramChannel.localAddress();
                writeMessage(context,new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS,Socks5AddressType.IPv4, socksOption.getHost(),bindAddr.getPort()),
                        v->{
                            releaseChannel(context);
                        });
            });




        }


        public static class SocksUdpEncoder extends MessageToMessageEncoder<DatagramPacket> {

            @Override
            protected void encode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) throws Exception {
                InetSocketAddress address = msg.sender();
                if (address ==null){
//                    out.add(msg.retain());
                    return;
                }
                byte type=0;
                byte[] addressBuf;
                if (address.isUnresolved()){
                    type=3;
                    addressBuf=address.getHostString().getBytes(StandardCharsets.UTF_8);
                }else{
                    InetAddress inetAddress = address.getAddress();
                    addressBuf=inetAddress.getAddress();
                    type= (byte) ((inetAddress instanceof Inet4Address)?1:4);
                }
                ByteBuf content=ctx.alloc().buffer(4+ addressBuf.length+2+msg.content().readableBytes());
                content.writeByte(0)
                        .writeByte(0)
                        .writeByte(0)
                        .writeByte(type);
                content.writeBytes(addressBuf);
                content.writeShort(address.getPort());
                content.writeBytes(msg.content());

                out.add(new DatagramPacket(content,msg.recipient()));


            }
        }


    }
}
