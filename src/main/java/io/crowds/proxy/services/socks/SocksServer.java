package io.crowds.proxy.services.socks;

import io.crowds.Platform;
import io.crowds.proxy.Axis;
import io.crowds.proxy.DatagramOption;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.socks.SocksInitRequestDecoder;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.handler.codec.socksx.v4.Socks4Message;
import io.netty.handler.codec.socksx.v5.*;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
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
        serverBootstrap
                .group(axis.getEventLoopGroup(),axis.getEventLoopGroup())
                .channel(Platform.getServerSocketChannelClass())
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
                dest=InetSocketAddress.createUnresolved(request.dstAddr(),request.dstPort());
            }else{
                dest=new InetSocketAddress(request.dstAddr(),request.dstPort());
            }
            return dest;
        }

        public void releaseChannel(ChannelHandlerContext ctx){
            ChannelPipeline pipeline = ctx.channel().pipeline();
            while (pipeline.removeFirst()!=this){

            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, SocksMessage msg) throws Exception {
            if (msg.version()== SocksVersion.SOCKS4a){
                pass=true;
                if (socksOption.isPassAuth()){
                    writeMessage(ctx,new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED),v->ctx.close());
                    return;
                }
                Socks4CommandRequest request= (Socks4CommandRequest) msg;
                writeMessage(ctx,new DefaultSocks4CommandResponse(Socks4CommandStatus.SUCCESS),
                        v->{
                            releaseChannel(ctx);
                            axis.handleTcp(ctx.channel(),ctx.channel().remoteAddress(),new InetSocketAddress(request.dstAddr(),request.dstPort()));
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
                }else if (msg instanceof Socks5CommandRequest){
                    if (!pass){
                        ctx.channel().close();
                        return;
                    }
                    Socks5CommandRequest request= (Socks5CommandRequest) msg;
                    if (request.type()==Socks5CommandType.UDP_ASSOCIATE){
                        //udp
                        handleUdpAssociate(ctx,request);
                    }else{
                        //tcp
                        InetSocketAddress dest = getAddress(request);
                        writeMessage(ctx,new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS,request.dstAddrType(),socksOption.getHost(),socksOption.getPort()),
                                v->axis.handleTcp(ctx.channel(),ctx.channel().remoteAddress(), dest));

                    }
                    releaseChannel(ctx);
                }
            }else{
                logger.warn("unknown socks version :{}",msg.version().byteValue());
                ctx.channel().close();
            }
        }

        private void handleUdpAssociate(ChannelHandlerContext ctx,Socks5CommandRequest request){
            InetSocketAddress dest = getAddress(request);
            Future<DatagramChannel> future=axis.getChannelCreator().createDatagramChannel(new DatagramOption(),
                    new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast(new SimpleChannelInboundHandler<DatagramPacket>(false) {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
                                    if (!msg.sender().equals(ctx.channel().remoteAddress())) {
                                        ReferenceCountUtil.safeRelease(msg);
                                        return;
                                    }
                                    axis.handleUdp((DatagramChannel) ctx.channel(), new DatagramPacket(msg.content(), dest, msg.sender()));
                                }
                            });
                }
            });
            future.addListener(f -> {
                if (!f.isSuccess()){
                    logger.error("{}",f.cause().getMessage());
                    ctx.close();
                    return;
                }
                DatagramChannel datagramChannel= (DatagramChannel) f.get();
                ctx.channel().closeFuture().addListener(it -> datagramChannel.close());
                InetSocketAddress bindAddr = datagramChannel.localAddress();
                writeMessage(ctx,new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS,request.dstAddrType(), bindAddr.getHostName(),bindAddr.getPort()),
                        v->{});
            });




        }


    }
}
