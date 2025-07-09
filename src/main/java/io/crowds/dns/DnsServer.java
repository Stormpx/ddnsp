package io.crowds.dns;


import io.crowds.Context;
import io.crowds.dns.server.DatagramDnsRequest;
import io.crowds.dns.server.DnsContext0;
import io.crowds.dns.server.SocketDnsRequest;
import io.crowds.proxy.common.IdleTimeoutHandler;
import io.crowds.util.Async;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.unix.UnixChannelOption;
import io.netty.handler.codec.dns.*;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class DnsServer {
    private final static Logger logger= LoggerFactory.getLogger(DnsServer.class);

    private final Context context;

    private DatagramChannel udpServer;
    private ServerChannel tcpServer;

    private DnsOption option;

    private final DnsProcessor processor;

    public DnsServer(Context context, DnsClient dnsClient) {
        this.context = context;
        this.processor=new DnsProcessor(dnsClient);

    }

    public DnsServer setOption(DnsOption option) {
        this.option = option;
        this.processor.setOption(this.option);
        return this;
    }


    public DnsServer dnsContextHandler(Handler<DnsContext0> contextHandler) {
        Objects.requireNonNull(contextHandler);
        this.processor.contextHandler(contextHandler);
        return this;
    }

    private Future<Void> startUdp(SocketAddress socketAddress){
        this.udpServer = context.getDatagramChannel();
        this.udpServer.config().setOption(ChannelOption.SO_REUSEADDR,true);
        if (Epoll.isAvailable()){
            this.udpServer.config().setOption(UnixChannelOption.SO_REUSEPORT,true);
        }
        this.udpServer.pipeline()
                      .addLast(new DatagramDnsQueryDecoder())
                      .addLast(new DatagramDnsResponseEncoder())
                      .addLast(new SimpleChannelInboundHandler<DatagramDnsQuery>(false) {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsQuery msg) throws Exception {
                            processor.process(new DatagramDnsRequest(ctx.channel(),msg));
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                            if (logger.isDebugEnabled()){
                                logger.error("",cause);
                            }else {
                                logger.warn("Dns udp Server exception occurred "+cause.getMessage());
                            }
                        }
                    });
        this.context.getEventLoopGroup().register(udpServer);
        return Async.toFuture(this.udpServer.bind(socketAddress));
    }

    private Future<Void> startTcp(SocketAddress socketAddress){
        var future = new ServerBootstrap()
                .group(context.getAcceptor(),context.getEventLoopGroup())
                .channelFactory(context.getServerChannelFactory())
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        socketChannel.pipeline()
                                .addLast(new TcpDnsQueryDecoder())
                                .addLast(new TcpDnsResponseEncoder())
                                .addLast(new SimpleChannelInboundHandler<DefaultDnsQuery>(false) {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, DefaultDnsQuery dnsQuery) throws Exception {
                                        processor.process(new SocketDnsRequest((SocketChannel) ctx.channel(),dnsQuery));
                                    }
                                    @Override
                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                        if (logger.isDebugEnabled()){
                                            logger.error("",cause);
                                        }else {
                                            logger.warn("Dns tcp server exception occurred "+cause.getMessage());
                                        }
                                    }
                                })
                                .addLast(new IdleTimeoutHandler(2, TimeUnit.MINUTES,(ch,idle)->ch.close()));
                    }
                })
                .bind(socketAddress);
        this.tcpServer = (ServerChannel) future.channel();
        return Async.toFuture(future);
    }

    public Future<Void> start(SocketAddress socketAddress){
        if (!this.option.isEnable()){
            return Future.succeededFuture();
        }
        Promise<Void> promise = Promise.promise();

        Future.join(startTcp(socketAddress),startUdp(socketAddress))
                .onComplete(future->{
                    if (future.succeeded()){
                        logger.info("start dns server {} success",socketAddress);
                        promise.tryComplete();
                    }else {
                        logger.info("start dns server {} failed cause:{}",socketAddress,future.cause().getMessage());
                        CompositeFuture cf = future.result();
                        if (cf.succeeded(0)){
                            this.tcpServer.close();
                        }
                        if (cf.succeeded(1)){
                            this.udpServer.close();
                        }
                        promise.tryFail(future.cause());
                    }
                });
        return promise.future();
    }



}
