package io.crowds;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.resolver.AddressResolverGroup;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.internal.VertxInternal;
import org.stormpx.net.PartialNetStack;

import java.net.InetSocketAddress;

public class Context {
    private final VertxInternal vertx;
    private final PartialNetStack netStack;
    private final AddressResolverGroup<?> nettyResolver;

    public Context(VertxInternal vertx, PartialNetStack netStack, AddressResolverGroup<?> nettyResolver) {
        this.vertx = vertx;
        this.netStack = netStack;
        this.nettyResolver = nettyResolver;
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

    public AddressResolverGroup<?> getNettyResolver() {
        return nettyResolver;
    }
}
