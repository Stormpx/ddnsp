package io.crowds.dns;


import io.crowds.Platform;
import io.crowds.util.DnsKit;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.codec.dns.*;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.net.impl.PartialPooledByteBufAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.*;

public class DnsServer {
    private final static Logger logger= LoggerFactory.getLogger(DnsServer.class);
    private EventLoopGroup eventLoopGroup;
    private DatagramChannel channel;

    private DnsClient dnsClient;

    private DnsOption option;

    private DnsCache dnsCache;

    private DnsProcessor processor;

    public DnsServer(EventLoopGroup eventLoopGroup,DnsClient dnsClient) {
        this.eventLoopGroup = eventLoopGroup;
        this.dnsClient=dnsClient;
        this.dnsCache=new DnsCache();
        this.channel= Platform.getDatagramChannel();
        channel.config().setAllocator(PartialPooledByteBufAllocator.DEFAULT);
        this.channel.pipeline()
                .addLast(new DatagramDnsQueryDecoder())
                .addLast(new DatagramDnsResponseEncoder())
                .addLast(new SimpleChannelInboundHandler<DnsQuery>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, DnsQuery msg) throws Exception {
                        processor.process(msg);
                    }
                });

        this.processor=new DnsProcessor(channel,dnsClient,dnsCache);

    }

    public DnsServer setOption(DnsOption option) {
        this.option = option;
        this.processor.setOption(this.option);
        return this;
    }


    public DnsServer contextHandler(Handler<DnsContext> contextHandler) {
        Objects.requireNonNull(contextHandler);
        this.processor.contextHandler(contextHandler);
        return this;
    }

    public Future<Void> start(SocketAddress socketAddress){
        Promise<Void> promise = Promise.promise();
        this.eventLoopGroup.register(channel);
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
