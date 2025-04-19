package io.crowds.proxy.common;

import io.crowds.compoments.dns.InternalDnsResolver;
import io.crowds.util.AddrType;
import io.crowds.util.Async;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class DynamicRecipientLookupHandler extends ChannelOutboundHandlerAdapter {

    private final InternalDnsResolver resolver;

    public DynamicRecipientLookupHandler(InternalDnsResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof DatagramPacket packet){
            InetSocketAddress recipient = packet.recipient();
            if (recipient.isUnresolved()){
                if (ctx.channel().localAddress() instanceof InetSocketAddress inetSocketAddress){
                    InetAddress address = inetSocketAddress.getAddress();
                    boolean ipv4 = address.isAnyLocalAddress()|| address instanceof Inet4Address;

                    Async.toCallback(
                            ctx.channel().eventLoop(),
                            resolver.resolve(recipient.getHostString(),ipv4? AddrType.IPV4:AddrType.IPV6)
                    ).addListener(f->{
                        if (!f.isSuccess()){
                            promise.tryFailure(f.cause());
                            ReferenceCountUtil.safeRelease(packet);
                            return;
                        }
                        var validPacket = new DatagramPacket(packet.content(),new InetSocketAddress((InetAddress) f.get(),recipient.getPort()),packet.sender());
                        ctx.write(validPacket,promise);
                    });
                    return;
                }
            }
        }
        super.write(ctx, msg, promise);
    }
}
