package io.crowds.proxy;

import io.crowds.Platform;
import io.crowds.proxy.services.socks.SocksServer;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.direct.DirectProxyTransport;
import io.crowds.proxy.transport.direct.TcpEndPoint;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

public class ProxyServer {
    private final static Logger logger= LoggerFactory.getLogger(ProxyServer.class);

    private ProxyOption proxyOption;
    private EventLoopGroup eventLoopGroup;
    private ChannelCreator channelCreator;

    private Axis axis;

    private SocksServer server;

    public ProxyServer(EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        this.channelCreator=new ChannelCreator(eventLoopGroup);
        this.axis=new Axis(eventLoopGroup,channelCreator);
    }

    public ProxyServer setProxyOption(ProxyOption proxyOption) {
        this.proxyOption = proxyOption;
        this.channelCreator.setProxyOption(proxyOption);
        return this;
    }

    public Future<Void> start(SocketAddress socketAddress) {

//        return startTcp(socketAddress);
        return CompositeFuture.any(startTcp(socketAddress), startUdp(socketAddress))
                .map((Void)null);
    }

    public Future<Void> start(){
        List<Future> futures=new ArrayList<>();
        if (proxyOption.getSocks()!=null){
            this.server = new SocksServer(proxyOption.getSocks(),this.axis);
            futures.add(server.start());
        }

        return CompositeFuture.any(futures)
                .map((Void)null);
    }


    private Future<Void> startTcp(SocketAddress socketAddress){
        Promise<Void> promise=Promise.promise();
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.channel(Platform.getServerSocketChannelClass())
                        .option(EpollChannelOption.IP_TRANSPARENT,true)
                        .childOption(EpollChannelOption.IP_TRANSPARENT,true);

//        serverBootstrap.channel(NioServerSocketChannel.class);
        serverBootstrap
                .group(eventLoopGroup,eventLoopGroup)
                .childHandler(new ChannelInitializer<SocketChannel>(){

                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new ProxyTcpInitializer());
                    }
                })
                .bind(socketAddress)
                .addListener(future -> {
                    if (future.isSuccess()) {
                        promise.complete();
                        logger.info("start tcp proxy server {}", socketAddress);
                    }else {
                        future.cause().printStackTrace();
                        promise.tryFail(future.cause());
                        logger.error("start tcp proxy server failed cause:{}", future.cause().getMessage());
                    }
                })
        ;
        return promise.future();
    }

    private Future<Void> startUdp(SocketAddress socketAddress){
        Promise<Void> promise=Promise.promise();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap
                .group(eventLoopGroup)
                .channel(Platform.getDatagramChannelClass())
                .option(EpollChannelOption.IP_TRANSPARENT,true)
                .option(EpollChannelOption.IP_RECVORIGDSTADDR,true)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(new ProxyUdpHandler());
                    }
                })
                .bind(socketAddress)
                .addListener(future -> {
                    if (future.isSuccess()) {
                        promise.complete();
                        logger.info("start udp proxy server {}", socketAddress);
                    }else {
                        promise.tryFail(future.cause());
                        logger.error("start udp proxy server failed cause:{}", future.cause().getMessage());
                    }
                });

        return promise.future();

    }


    private ProxyTransport getTransport(ChannelHandlerContext ctx){
        Channel channel = ctx.channel();
//        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16823);
//        var option=new VmessOption()
//                .setConnIdle(300)
//                .setAddress(dest)
//                .setSecurity(Security.ChaCha20_Poly1305)
//                .setUser(new User(UUID.fromString("b831381d-6324-4d53-ad4f-8cda48b30811"),0));
//        return new VmessProxyTransport(option,eventLoopGroup,channelCreator);
        return new DirectProxyTransport(eventLoopGroup,channelCreator);
    }

    private void bridging(EndPoint src,EndPoint dest,NetLocation netLocation){
        var proxyCtx=new ProxyContext(src,dest,netLocation);
        dest.bufferHandler(src::write);
        src.bufferHandler(dest::write);
    }


    private class ProxyUdpHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        public ProxyUdpHandler() {
            super(false);
        }

        private DatagramChannel createNonLocalChannel(InetSocketAddress address){
            return channelCreator.createDatagramChannel(address,new DatagramOption().setBindAddr(address).setIpTransport(true));
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
            InetSocketAddress recipient = msg.recipient();
            InetSocketAddress sender = msg.sender();
            logger.info("udp data packet receive sender: {} recipient: {}",sender,recipient);
            axis.handleUdp(createNonLocalChannel(recipient),msg);

//            var src=new UdpEndPoint(createNonLocalChannel(recipient),sender);
//            ProxyTransport provider = getTransport(ctx);
//            NetLocation netLocation = new NetLocation(sender, recipient, TP.UDP);
//            provider.createEndPoint(netLocation)
//                .addListener(future -> {
//                    if (!future.isSuccess()){
//                        logger.info("......");
//                        return;
//                    }
//                    EndPoint dest= (EndPoint) future.get();
//
////                    dest.channel().pipeline().addLast(new ProxyChannelHandler(proxyCtx,1));
//                    bridging(src,dest,netLocation);
//
//                    dest.write(msg.content());
//                });
        }
    }

    private class ProxyTcpInitializer extends ChannelInboundHandlerAdapter{

        private ProxyContext proxyContext;

        private SocketAddress selectRemoteAddress(Channel channel){
            return channel.localAddress();
//            return new InetSocketAddress("192.168.31.117",8889);
        }


        private void initContext(ChannelHandlerContext ctx){
            Channel channel = ctx.channel();
            channel.config().setAutoRead(false);
            ProxyTransport transport = getTransport(ctx);
            TcpEndPoint src = new TcpEndPoint(channel);
            logger.info("channel local {} remote {}", channel.localAddress(), channel.remoteAddress());
            NetLocation netLocation = new NetLocation(channel.remoteAddress(), selectRemoteAddress(channel), TP.TCP);
            transport.createEndPoint(netLocation)
                .addListener(future -> {
                    if (!future.isSuccess()){
                        if (logger.isDebugEnabled())
                            logger.error("",future.cause());
                        logger.error("connect remote: {} failed cause: {}",netLocation.getDest().getAddress(),future.cause().getMessage());
                        channel.close();
                        return;
                    }
                    EndPoint dest= (EndPoint) future.get();
//                    dest.channel().pipeline().addLast(new ProxyChannelHandler(proxyContext,1));
//                    src.channel().pipeline().addLast(new ProxyChannelHandler(proxyContext,0));
                    bridging(src,dest,netLocation);

                    channel.config().setAutoRead(true);
                });


        }



        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
//            initContext(ctx);
            Channel channel = ctx.channel();
            axis.handleTcp(channel, channel.remoteAddress(),selectRemoteAddress(channel));
            super.channelActive(ctx);
        }

    }

}
