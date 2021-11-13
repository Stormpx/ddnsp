package io.crowds.proxy.services.transparent;

import io.crowds.Platform;
import io.crowds.proxy.Axis;
import io.crowds.proxy.DatagramOption;
import io.crowds.proxy.ProxyContext;
import io.crowds.proxy.common.BaseChannelInitializer;
import io.crowds.proxy.transport.UdpChannel;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class TransparentServer {
    private final static Logger logger= LoggerFactory.getLogger(TransparentServer.class);

    private TransparentOption option;
    private Axis axis;

    public TransparentServer(TransparentOption option, Axis axis) {
        this.option = option;
        this.axis = axis;
    }

    public Future<Void> start(){
        InetSocketAddress socketAddress = new InetSocketAddress(option.getHost(), option.getPort());
        if (!Epoll.isAvailable()){
            logger.error("unable start transparent server because :{}",Epoll.unavailabilityCause().getCause().getMessage());
            return Future.failedFuture("");
        }
        return CompositeFuture.any(startTcp(socketAddress),startUdp(socketAddress))
                .map((Void)null);

    }

    private Future<Void> startTcp(SocketAddress socketAddress){
        Promise<Void> promise=Promise.promise();

        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.channel(Platform.getServerSocketChannelClass())
                .option(EpollChannelOption.IP_TRANSPARENT,true)
                .childOption(EpollChannelOption.IP_TRANSPARENT,true);

        serverBootstrap
                .group(axis.getEventLoopGroup(),axis.getEventLoopGroup())
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
                        logger.info("start transparent tcp proxy server {}", socketAddress);
                    }else {
                        future.cause().printStackTrace();
                        promise.tryFail(future.cause());
                        logger.error("start transparent tcp proxy server failed cause:{}", future.cause().getMessage());
                    }
                })
        ;
        return promise.future();
    }

    private Future<Void> startUdp(SocketAddress socketAddress){
        Promise<Void> promise=Promise.promise();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap
                .group(axis.getEventLoopGroup())
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
                        logger.info("start transparent udp proxy server {}", socketAddress);
                    }else {
                        promise.tryFail(future.cause());
                        logger.error("start transparent udp proxy server failed cause:{}", future.cause().getMessage());
                    }
                });

        return promise.future();

    }


    private class ProxyUdpHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        public ProxyUdpHandler() {
            super(false);
        }

        private io.netty.util.concurrent.Future<DatagramChannel> createNonLocalChannel(ChannelHandlerContext ctx, InetSocketAddress address){
            io.netty.util.concurrent.Promise<DatagramChannel> promise = ctx.executor().newPromise();
            axis.getChannelCreator().createDatagramChannel("transparent",address,new DatagramOption().setBindAddr(address).setIpTransport(true),
                    new BaseChannelInitializer().connIdle(300))
                    .addListener(future -> {
                        if (!future.isSuccess()){
                            promise.tryFailure(future.cause());
                            return;
                        }
                        UdpChannel udpChannel= (UdpChannel) future.getNow();
                        promise.trySuccess(udpChannel.getDatagramChannel());
                    });

            return promise;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {

            InetSocketAddress recipient = msg.recipient();
            InetSocketAddress sender = msg.sender();
            if (logger.isDebugEnabled())
                logger.debug("udp data packet receive sender: {} recipient: {}",sender,recipient);
            createNonLocalChannel(ctx,recipient)
                    .addListener((FutureListener<DatagramChannel>) future -> {
                        if (!future.isSuccess()){
                            logger.error("{}",future.cause().getMessage());
                            return;
                        }
                        DatagramChannel datagramChannel= future.get();
                        axis.handleUdp(datagramChannel,msg);
                    });

        }
    }

    private class ProxyTcpInitializer extends ChannelInboundHandlerAdapter {


        private SocketAddress selectRemoteAddress(Channel channel){
            return channel.localAddress();
            //            return new InetSocketAddress("192.168.31.117",8889);
        }


        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            Channel channel = ctx.channel();
            SocketAddress remoteAddress = selectRemoteAddress(channel);
            if (logger.isDebugEnabled())
                logger.debug("tcp remote addr:{}",remoteAddress);
            axis.handleTcp(channel, channel.remoteAddress(), remoteAddress);
            super.channelActive(ctx);
        }

    }
}
