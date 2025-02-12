package io.crowds;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.vertx.core.impl.VertxImpl;

public class Context {
    private final VertxImpl vertx;

    public Context(VertxImpl vertx) {
        this.vertx = vertx;
    }

    public EventLoopGroup getAcceptor(){
        return vertx.getAcceptorEventLoopGroup();
    }
    public EventLoopGroup getEventLoopGroup(){
        return vertx.getEventLoopGroup();
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

}
