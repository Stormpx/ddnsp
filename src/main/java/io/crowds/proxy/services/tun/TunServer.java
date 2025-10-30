package io.crowds.proxy.services.tun;

import io.crowds.Context;
import io.crowds.proxy.Axis;
import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.common.TcpTransparentHandler;
import io.crowds.proxy.common.UdpTransparentHandler;
import io.crowds.util.ChannelFactoryProvider;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.socket.SocketChannel;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stormpx.net.PartialNetStack;
import org.stormpx.net.RouteItem;
import org.stormpx.net.netty.PartialChannelOption;
import org.stormpx.net.netty.PartialDatagramChannel;
import org.stormpx.net.netty.PartialServerSocketChannel;
import org.stormpx.net.network.IfType;
import org.stormpx.net.network.NetworkParams;
import org.stormpx.net.socket.PartialSocketOptions;
import org.stormpx.net.util.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class TunServer {
    private static final Logger logger = LoggerFactory.getLogger(TunServer.class);


    private final TunServerOption option;
    private final Axis axis;
    private final DefaultEventLoop executor;
    private final List<SubNet> ignoreAddresses;

    private final ChannelCreator paritialChannelCreator;

    private PartialServerSocketChannel tcpServer;
    private PartialDatagramChannel udpServer;


    public TunServer(TunServerOption option, Axis axis) {
        this.option = option;
        this.axis = axis;
        this.executor = new DefaultEventLoop();
        this.ignoreAddresses = parseIgnoreAddresses(option);

        this.paritialChannelCreator = new ChannelCreator(axis.getContext().getEventLoopGroup(), ChannelFactoryProvider.ofPartial(),axis.getContext().getVariantResolver());
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
                logger.warn("Mask can not found:{}",ignoreAddress);
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
                       .option(PartialChannelOption.of(PartialSocketOptions.TRANSPARENT_PROXY), this::acceptTunPacket)
                       .option(PartialChannelOption.of(PartialSocketOptions.IP_TRANSPARENT),true);

        serverBootstrap
                .group(context.getAcceptor(),context.getEventLoopGroup())
                .childHandler(new ChannelInitializer<SocketChannel>(){
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new TcpTransparentHandler(axis));
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
                         ch.pipeline().addLast(new UdpTransparentHandler(axis,paritialChannelCreator));
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


}
