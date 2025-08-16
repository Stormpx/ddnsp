package io.crowds.dns.server;

import io.crowds.dns.DnsProcessor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.handler.codec.dns.DnsQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalDnsQueryHandler extends SimpleChannelInboundHandler<DnsQuery> {

    private static final Logger logger = LoggerFactory.getLogger(LocalDnsQueryHandler.class);
    private final DnsProcessor processor;
    private final LocalMsgType msgType;

    public LocalDnsQueryHandler(DnsProcessor processor, LocalMsgType msgType) {
        super(false);
        this.processor = processor;
        this.msgType = msgType;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DnsQuery msg) throws Exception {
        var dnsRequest = switch (msgType){
            case LocalMsgType.Datagram datagram -> new DatagramDnsRequest(ctx.channel(), (DatagramDnsQuery) msg);
            case LocalMsgType.Stream stream -> new SocketDnsRequest(ctx.channel(), stream.sender(), (DefaultDnsQuery) msg);
        };
        processor.process(dnsRequest);
    }
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (logger.isDebugEnabled()){
            logger.error("",cause);
        }else {
            logger.warn("Dns local Server exception occurred "+cause.getMessage());
        }
    }
}
