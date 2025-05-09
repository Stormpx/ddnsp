package io.crowds.proxy.services.tun;

import io.crowds.Context;
import io.crowds.Ddnsp;
import io.crowds.proxy.Axis;
import io.crowds.proxy.common.BaseChannelInitializer;
import io.crowds.proxy.dns.FakeContext;
import io.crowds.proxy.dns.FakeDns;
import io.crowds.util.AddrType;
import io.crowds.util.Async;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stormpx.net.PartialNetStack;
import org.stormpx.net.RouteItem;
import org.stormpx.net.network.IfType;
import org.stormpx.net.network.NetworkParams;
import org.stormpx.net.socket.PartialSocketOptions;
import org.stormpx.net.util.*;
import org.stormpx.net.netty.*;

import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class TunServer {
    private static final Logger logger = LoggerFactory.getLogger(TunServer.class);


    private final TunServerOption option;
    private final Axis axis;
    private final DefaultEventLoop executor;
    private final List<SubNet> ignoreAddresses;

    private PartialServerSocketChannel tcpServer;
    private PartialDatagramChannel udpServer;

    private final Map<InetSocketAddress, io.netty.util.concurrent.Future<DatagramChannel>> foreignChannelLookups;

    public TunServer(TunServerOption option, Axis axis) {
        this.option = option;
        this.axis = axis;
        this.foreignChannelLookups=new ConcurrentHashMap<>();
        this.executor = new DefaultEventLoop();
        this.ignoreAddresses = parseIgnoreAddresses(option);
    }


    private List<SubNet> parseIgnoreAddresses(TunServerOption option){
        var ignoreAddresses = option.getIgnoreAddress();
        if (ignoreAddresses==null||ignoreAddresses.isEmpty()){
            return List.of();
        }
        List<SubNet> result=new ArrayList<>();
        for (Object content : ignoreAddresses) {
            if (!(content instanceof String ignoreAddress)){
                continue;
            }
            String[] strings = ignoreAddress.split("/");
            if (strings.length!=2){
                logger.warn("Unrecognized address:{}",ignoreAddress);
                continue;
            }
            IP ip = null;
            int mask;
            try {
                ip = IP.parse(strings[0]);
            }catch (RuntimeException e){
                logger.warn("Invalid address:{}",ip);
                continue;
            }
            try {
                mask = Integer.parseInt(strings[1]);
            } catch (NumberFormatException e) {
                logger.warn("mask can not found:{}",ignoreAddress);
                continue;
            }
            result.add(new SubNet(ip,mask));
        }
        return result;
    }

    private boolean acceptTunPacket(ProxyInfo info){
        IPPort dst = info.dst();
        IP dstIp = dst.ip();
        if (!dstIp.isUnicast()|| dstIp.isLinkLocal()|| dstIp.isUnspecified()){
            return false;
        }
        for (SubNet ignoreAddress : ignoreAddresses) {
            if (ignoreAddress.match(dstIp)){
                return false;
            }
        }
        return true;
    }

    private Future<Void> startTcpServer(InetSocketAddress bindAddress){
        Promise<Void> promise=Promise.promise();
        Context context = axis.getContext();
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.channel(PartialServerSocketChannel.class)
                       .option(PartialChannelOption.of(PartialSocketOptions.TRANSPARENT_PROXY), this::acceptTunPacket);

        serverBootstrap
                .group(context.getAcceptor(),context.getEventLoopGroup())
                .childHandler(new ChannelInitializer<SocketChannel>(){
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new ProxyTcpInitializer());
                    }
                })
                .bind(bindAddress)
                .addListener(future -> {
                    if (future.isSuccess()) {
                        logger.info("start tun tcp proxy server");
                        this.tcpServer = (PartialServerSocketChannel) future.get();
                        promise.complete();
                    }else {
                        logger.error("start tun tcp proxy server failed cause:{}", future.cause().getMessage());
                        promise.tryFail(future.cause());
                    }
                })
        ;
        return promise.future();
    }

    private Future<Void> startUdpServer(InetSocketAddress bindAddress){
        Promise<Void> promise=Promise.promise();
        Context context = axis.getContext();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(context.getEventLoopGroup())
                 .channel(PartialDatagramChannel.class)
                 .option(PartialChannelOption.of(PartialSocketOptions.TRANSPARENT_PROXY),this::acceptTunPacket)
                 .handler(new ChannelInitializer<>() {
                     @Override
                     protected void initChannel(Channel ch) throws Exception {
                         ch.pipeline().addLast(new ProxyUdpHandler());
                     }
                 });
        ChannelFuture cf = bootstrap.bind(bindAddress);
        cf.addListener(f->{
            if (!f.isSuccess()){
                promise.tryFail(f.cause());
                return;
            }
            this.udpServer = (PartialDatagramChannel) cf.channel();
            logger.info("start tun udp proxy server");
            promise.complete();
        });
        return promise.future();

    }

    public Future<Void> start(){
//        if (!Platform.isLinux()){
//            return Future.failedFuture("currently only supports linux");
//        }
        try {
            InetSocketAddress bindAddress = new InetSocketAddress(InetAddress.getByAddress(IPv4.LOOPBACK.getBytes()),5474);
            PartialNetStack netStack = axis.getContext().getNetStack();
            NetworkParams params = new NetworkParams()
                    .setMtu(option.getMtu())
                    .setSubNet(new SubNet(IPv4.LOOPBACK,8))
                    .setVerifyChecksum(false)
                    .setIfType(IfType.INTERNET);
            netStack.addNetwork(option.getName(), params,()->new TunIface(executor, option.getName()));
            netStack.addRoute(new RouteItem().setDestination(new SubNet(IPv4.UNSPECIFIED,0)).setNetwork(option.getName()));

            return Future.any(startTcpServer(bindAddress),startUdpServer(bindAddress)).map((v)->null);
        } catch (UnknownHostException e) {
            return Future.failedFuture("Should not happen");
        }

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

    private class ProxyUdpHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        public ProxyUdpHandler() {
            super(false);
        }

        private io.netty.util.concurrent.Future<DatagramChannel> doCreateDatagramChannel(SocketAddress bindAddr){
            PartialDatagramChannel datagramChannel = new PartialDatagramChannel();
            datagramChannel.pipeline().addLast(new BaseChannelInitializer().connIdle(300,(ch,idleStateEvent) -> ch.close()));
            var eventLoop = axis.getContext().getEventLoopGroup().next();
            eventLoop.register(datagramChannel);

            io.netty.util.concurrent.Promise<DatagramChannel> promise=eventLoop.newPromise();
            datagramChannel.bind(bindAddr)
                           .addListener(future -> {
                               if (!future.isSuccess()){
                                   promise.tryFailure(future.cause());
                                   return;
                               }
                               promise.trySuccess(datagramChannel);
                           });
            return promise;
        }

        private io.netty.util.concurrent.Future<DatagramChannel> createForeignChannel(InetSocketAddress address)  {
            if (executor.inEventLoop()){
                io.netty.util.concurrent.Future<DatagramChannel> result = foreignChannelLookups.get(address);
                if (result!=null){
                    return result;
                }
                var future = doCreateDatagramChannel(address);
                if (future.isDone()&&!future.isSuccess()){
                    return future;
                }
                foreignChannelLookups.put(address,future);
                future.addListener((FutureListener<DatagramChannel>) f->{
                    if (!f.isSuccess()){
                        foreignChannelLookups.remove(address,future);
                        return;
                    }
                    f.get().closeFuture().addListener(it->foreignChannelLookups.remove(address,future));
                });
                return future;
            }else{
                io.netty.util.concurrent.Promise<DatagramChannel> promise = TunServer.this.executor.newPromise();
                TunServer.this.executor.submit(()-> createForeignChannel(address).addListener(
                        Async.cascade(promise)));
                return promise;
            }
        }

        private void handleFallbackPacket(DatagramPacket packet)  {
            InetSocketAddress sender = packet.sender();
            if (sender.isUnresolved()) {
                var fakeAddr = getFakeAddress(sender, AddrType.of(packet.recipient()));
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
                            logger.error("Bind netStack address:{} failed cause:{}", finalSender,future.cause().getMessage());
                            ReferenceCountUtil.safeRelease(packet);
                            return;
                        }
                        DatagramChannel datagramChannel= future.get();
                        datagramChannel.writeAndFlush(packet);
                    });
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
                            logger.error("Bind netStack address:{} failed cause:{}", recipient,future.cause().getMessage());
                            ReferenceCountUtil.safeRelease(msg);
                            return;
                        }
                        DatagramChannel datagramChannel= future.get();
                        axis.handleUdp0(datagramChannel,msg, this::handleFallbackPacket);
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
