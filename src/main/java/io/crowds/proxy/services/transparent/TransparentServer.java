package io.crowds.proxy.services.transparent;

import io.crowds.Context;
import io.crowds.proxy.Axis;
import io.crowds.proxy.ProxyContext;
import io.crowds.proxy.common.TcpTransparentHandler;
import io.crowds.proxy.common.UdpTransparentHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.unix.UnixChannelOption;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class TransparentServer {
    private final static Logger logger= LoggerFactory.getLogger(TransparentServer.class);

    private TransparentOption option;
    private Axis axis;
    private AtomicBoolean logSuccess;

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
                        ch.attr(ProxyContext.SEND_ZC_SUPPORTED);
                        ch.pipeline().addLast(new TcpTransparentHandler(axis));
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
                        ch.pipeline().addLast(new UdpTransparentHandler(axis,axis.getChannelCreator()));
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

}
