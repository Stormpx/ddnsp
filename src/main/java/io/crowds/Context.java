package io.crowds;

import io.crowds.compoments.dns.InternalDnsResolver;
import io.crowds.compoments.dns.VariantResolver;
import io.crowds.util.ChannelFactoryProvider;
import io.crowds.util.DatagramChannelFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.resolver.AddressResolverGroup;
import io.vertx.core.internal.VertxInternal;
import org.stormpx.net.PartialNetStack;

import java.util.function.Supplier;

public class Context {
    private final VertxInternal vertx;
    private final PartialNetStack netStack;
    private final VariantResolver variantResolver;
    private final ChannelFactoryProvider channelFactoryProvider;

    public Context(VertxInternal vertx, PartialNetStack netStack, Supplier<InternalDnsResolver> resolver) {
        this.vertx = vertx;
        this.netStack = netStack;
        this.variantResolver = new VariantResolver(resolver);
        this.channelFactoryProvider = ChannelFactoryProvider.of(vertx.transport());
    }

    public VertxInternal getVertx() {
        return vertx;
    }

    public EventLoopGroup getAcceptor(){
        return vertx.acceptorEventLoopGroup();
    }
    public EventLoopGroup getEventLoopGroup(){
        return vertx.eventLoopGroup();
    }

    public ChannelFactoryProvider getChannelFactoryProvider() {
        return channelFactoryProvider;
    }

    public DatagramChannel getDatagramChannel(InternetProtocolFamily family){
        return vertx.transport().datagramChannel(family);
    }
    public DatagramChannel getDatagramChannel(){
        return vertx.transport().datagramChannel();
    }

    public ChannelFactory<? extends ServerChannel> getServerChannelFactory(){
        return vertx.transport().serverChannelFactory(false);
    }
    public ChannelFactory<? extends Channel> getSocketChannelFactory(){
        return vertx.transport().channelFactory(false);
    }

    public PartialNetStack getNetStack() {
        return netStack;
    }

    public VariantResolver getVariantResolver() {
        return variantResolver;
    }

    public AddressResolverGroup<?> getNettyResolver() {
        return variantResolver.getNettyResolver();
    }
}
