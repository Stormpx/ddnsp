package io.crowds.dns;


import io.crowds.Context;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.unix.UnixChannelOption;
import io.netty.handler.codec.dns.*;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.*;

public class DnsServer {
    private final static Logger logger= LoggerFactory.getLogger(DnsServer.class);

    private final Context context;

    private final DatagramChannel channel;

    private DnsOption option;

    private final DnsProcessor processor;

    public DnsServer(Context context, DnsClient dnsClient) {
        this.context = context;
        this.channel= context.getDatagramChannel();
        this.channel.config().setOption(ChannelOption.SO_REUSEADDR,true);
        if (Epoll.isAvailable()){
            this.channel.config().setOption(UnixChannelOption.SO_REUSEPORT,true);
        }
        this.channel.pipeline()
                .addLast(new DatagramDnsQueryDecoder())
                .addLast(new DatagramDnsResponseEncoder())
                .addLast(new SimpleChannelInboundHandler<DnsQuery>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, DnsQuery msg) throws Exception {
                        processor.process(msg);
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        if (logger.isDebugEnabled()){
                            logger.error("",cause);
                        }else {
                            logger.warn("server exception occurred "+cause.getMessage());
                        }
                    }
                });

        this.processor=new DnsProcessor(channel,dnsClient);

    }

    public DnsServer setOption(DnsOption option) {
        this.option = option;
        this.processor.setOption(this.option);
        return this;
    }


    public DnsServer dnsContextHandler(Handler<DnsContext> contextHandler) {
        Objects.requireNonNull(contextHandler);
        this.processor.contextHandler(contextHandler);
        return this;
    }

    public Future<Void> start(SocketAddress socketAddress){
        if (!this.option.isEnable()){
            return Future.succeededFuture();
        }
        Promise<Void> promise = Promise.promise();
        this.context.getEventLoopGroup().register(channel);
        this.channel.bind(socketAddress).addListener(future -> {
            if (future.isSuccess()){
                logger.info("start dns server {} success",socketAddress);
                promise.tryComplete();
            }else{
                logger.info("start dns server {} failed cause:{}",socketAddress,future.cause().getMessage());
                promise.tryFail(future.cause());
            }
        });
        return promise.future();
    }



}
