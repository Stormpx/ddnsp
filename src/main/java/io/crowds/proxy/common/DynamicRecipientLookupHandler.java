package io.crowds.proxy.common;

import io.crowds.compoments.dns.InternalDnsResolver;
import io.crowds.util.AddrType;
import io.crowds.util.Async;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

public class DynamicRecipientLookupHandler extends ChannelDuplexHandler {

    private final InternalDnsResolver resolver;
    private final Map<InetAddress,String> ipDomains = new HashMap<>(4);

    public DynamicRecipientLookupHandler(InternalDnsResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof DatagramPacket packet){
            InetSocketAddress sender = packet.sender();
            if (sender!=null&&!sender.isUnresolved()){
                String hostname = ipDomains.get(sender.getAddress());
                if (hostname!=null){
                    ctx.fireChannelRead(new DatagramPacket(packet.content(),packet.recipient(),InetSocketAddress.createUnresolved(hostname,sender.getPort())));
                    return;
                }
            }
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof DatagramPacket packet){
            InetSocketAddress recipient = packet.recipient();
            if (recipient.isUnresolved()){
                if (ctx.channel().localAddress() instanceof InetSocketAddress inetSocketAddress){
                    InetAddress address = inetSocketAddress.getAddress();
                    boolean ipv4 = address.isAnyLocalAddress()|| address instanceof Inet4Address;
                    String hostname = recipient.getHostString();
                    Async.toCallback(
                            ctx.channel().eventLoop(),
                            resolver.resolve(hostname,ipv4? AddrType.IPV4:AddrType.IPV6)
                    ).addListener(f->{
                        if (!f.isSuccess()){
                            promise.tryFailure(f.cause());
                            ReferenceCountUtil.safeRelease(packet);
                            return;
                        }
                        InetAddress inetAddress = (InetAddress) f.get();
                        this.ipDomains.put(inetAddress,hostname);
                        var validPacket = new DatagramPacket(packet.content(),new InetSocketAddress(inetAddress,recipient.getPort()),packet.sender());
                        ctx.write(validPacket,promise);
                    });
                    return;
                }
            }
        }
        super.write(ctx, msg, promise);
    }
}
