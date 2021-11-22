package io.crowds.proxy.services.transparent;

import io.crowds.Platform;
import io.crowds.proxy.Axis;
import io.crowds.proxy.DatagramOption;
import io.crowds.proxy.ProxyContext;
import io.crowds.proxy.common.BaseChannelInitializer;
import io.crowds.proxy.dns.FakeContext;
import io.crowds.proxy.dns.FakeDns;
import io.crowds.proxy.transport.UdpChannel;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.unix.UnixChannelOption;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class TransparentServer {
    private final static Logger logger= LoggerFactory.getLogger(TransparentServer.class);

    private TransparentOption option;
    private Axis axis;
    private boolean logSuccess;

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

        List<Future> udpf=StreamSupport.stream(axis.getEventLoopGroup().spliterator(),false)
                .map(v->this.startUdp(socketAddress))
                .collect(Collectors.toList());
        return CompositeFuture.any(startTcp(socketAddress),CompositeFuture.all(udpf).map((Void)null))
                .map((Void)null);

    }

    private Future<Void> startTcp(SocketAddress socketAddress){
        Promise<Void> promise=Promise.promise();

        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.channel(Platform.getServerSocketChannelClass())
                .option(ChannelOption.SO_REUSEADDR,true)
                .option(UnixChannelOption.SO_REUSEPORT,true)
                .option(EpollChannelOption.IP_TRANSPARENT,true)
                .childOption(EpollChannelOption.IP_TRANSPARENT,true)
        ;

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
                .option(ChannelOption.SO_REUSEADDR,true)
                .option(UnixChannelOption.SO_REUSEPORT,true)
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
                        if (!this.logSuccess) {
                            logger.info("start transparent udp proxy server {}", socketAddress);
                            this.logSuccess=true;
                        }
                    }else {
                        promise.tryFail(future.cause());
                        logger.error("start transparent udp proxy server failed cause:{}", future.cause().getMessage());
                    }
                });

        return promise.future();

    }


    private boolean accept(InetSocketAddress destAddr){
        InetAddress dest = destAddr.getAddress();
        FakeDns fakeDns = axis.getFakeDns();
        if (fakeDns !=null&&fakeDns.isFakeIp(dest)){
            FakeContext fake = fakeDns.getFake(dest);
            return fake != null;
        }
        return true;
    }

    private class ProxyUdpHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        public ProxyUdpHandler() {
            super(false);
        }

        private io.netty.util.concurrent.Future<DatagramChannel> createNonLocalChannel(ChannelHandlerContext ctx, InetSocketAddress address) throws ExecutionException, InterruptedException {
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
            if (recipient.getPort()==0){
                ReferenceCountUtil.safeRelease(msg);
                return;
            }
            if (!accept(recipient)){
                ReferenceCountUtil.safeRelease(msg);
                return;
            }
            createNonLocalChannel(ctx,recipient)
                    .addListener((FutureListener<DatagramChannel>) future -> {
                        if (!future.isSuccess()){
                            logger.error("bind addr:{} failed cause:{}",recipient,future.cause().getMessage());
                            ReferenceCountUtil.safeRelease(msg);
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
        }


        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            Channel channel = ctx.channel();
            SocketAddress remoteAddress = selectRemoteAddress(channel);
            if (logger.isDebugEnabled())
                logger.debug("tcp remote addr:{}",remoteAddress);
            if (!accept((InetSocketAddress) remoteAddress)){
                ctx.close();
                return;
            }
            axis.handleTcp(channel, channel.remoteAddress(), remoteAddress);
            super.channelActive(ctx);
        }

    }
}
