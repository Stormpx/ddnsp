package io.crowds.proxy.services.transparent;

import io.crowds.Context;
import io.crowds.Platform;
import io.crowds.proxy.Axis;
import io.crowds.proxy.DatagramOption;
import io.crowds.proxy.common.BaseChannelInitializer;
import io.crowds.proxy.dns.FakeContext;
import io.crowds.proxy.dns.FakeDns;
import io.crowds.util.AddrType;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollMode;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.unix.UnixChannelOption;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class TransparentServer {
    private final static Logger logger= LoggerFactory.getLogger(TransparentServer.class);

    private TransparentOption option;
    private Axis axis;
    private AtomicBoolean logSuccess;


    private final Consumer<DatagramPacket> PACKET_HANDLER = this::handleFallbackPacket;

    public TransparentServer(TransparentOption option, Axis axis) {
        this.option = option;
        this.axis = axis;
        this.logSuccess=new AtomicBoolean(false);
    }

    public Future<Void> start(){
        InetSocketAddress socketAddress = new InetSocketAddress(option.getHost(), option.getPort());
        if (!Epoll.isAvailable()){
            logger.error("unable start transparent server because :{}",Epoll.unavailabilityCause().getCause().getMessage());
            return Future.failedFuture("");
        }

        List<Future<?>> udpf=StreamSupport.stream(axis.getContext().getEventLoopGroup().spliterator(),false)
                .map(v->this.startUdp(socketAddress))
                .collect(Collectors.toList());
        return Future.any(startTcp(socketAddress),Future.all(udpf).map((Void)null))
                .map((Void)null);

    }

    private Future<Void> startTcp(SocketAddress socketAddress){
        Promise<Void> promise=Promise.promise();
        Context context = axis.getContext();
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.channelFactory(context.getServerChannelFactory())
                       .option(ChannelOption.SO_REUSEADDR,true)
                       .option(UnixChannelOption.SO_REUSEPORT,true)
                       .option(EpollChannelOption.IP_TRANSPARENT,true)
                       .childOption(EpollChannelOption.IP_TRANSPARENT,true);

        serverBootstrap
                .group(context.getAcceptor(),context.getEventLoopGroup())
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
        Context context = axis.getContext();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap
                .group(context.getEventLoopGroup())
                .channelFactory(context::getDatagramChannel)
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
                        if (this.logSuccess.compareAndSet(false,true)) {
                            logger.info("start transparent udp proxy server {}", socketAddress);
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


    private io.netty.util.concurrent.Future<DatagramChannel> createForeignChannel(InetSocketAddress address)  {
        return  axis.getChannelCreator().createDatagramChannel(
                new DatagramOption().setBindAddr(address).setIpTransport(true),
                new BaseChannelInitializer().connIdle(300,(ch,idleStateEvent) -> ch.close())
        );
    }

    private InetSocketAddress getFakeAddress(InetSocketAddress address, AddrType addrType){
        if (!address.isUnresolved()){
            return address;
        }
        FakeDns fakeDns = axis.getFakeDns();
        if (fakeDns==null){
            return null;
        }
        FakeContext fakeContext = fakeDns.getFake(address.getHostString(), addrType);
        return fakeContext != null ? new InetSocketAddress(fakeContext.getFakeAddr(), address.getPort()) : null;
    }

    private void handleFallbackPacket(DatagramPacket packet)  {
        InetSocketAddress sender = packet.sender();
        if (sender.isUnresolved()) {
            var fakeAddr = getFakeAddress(sender,AddrType.of(packet.recipient()));
            if (fakeAddr==null){
                logger.warn("The fake address of the {} cannot be found. drop the packet",sender);
                return;
            }
            sender=fakeAddr;
        }
        InetSocketAddress finalSender = sender;
        createForeignChannel(sender)
                .addListener((FutureListener<DatagramChannel>) future -> {
                    if (!future.isSuccess()){
                        logger.error("bind addr:{} failed cause:{}", finalSender,future.cause().getMessage());
                        ReferenceCountUtil.safeRelease(packet);
                        return;
                    }
                    DatagramChannel datagramChannel= future.get();
                    datagramChannel.writeAndFlush(packet);
                });

    }


    private class ProxyUdpHandler extends SimpleChannelInboundHandler<DatagramPacket> {


        public ProxyUdpHandler() {
            super(false);
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
            createForeignChannel(recipient)
                    .addListener((FutureListener<DatagramChannel>) future -> {
                        if (!future.isSuccess()){
                            logger.error("bind addr:{} failed cause:{}",recipient,future.cause().getMessage());
                            ReferenceCountUtil.safeRelease(msg);
                            return;
                        }
                        DatagramChannel datagramChannel= future.get();
                        axis.handleUdp0(datagramChannel,msg, PACKET_HANDLER);
                    });

        }
    }

    private class ProxyTcpInitializer extends ChannelInboundHandlerAdapter {

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            Channel channel = ctx.channel();
            SocketAddress remoteAddress = channel.localAddress();
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
