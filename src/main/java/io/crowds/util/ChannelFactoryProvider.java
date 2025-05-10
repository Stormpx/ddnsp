package io.crowds.util;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.DatagramChannel;
import io.vertx.core.spi.transport.Transport;
import org.stormpx.net.netty.PartialDatagramChannel;
import org.stormpx.net.netty.PartialServerSocketChannel;
import org.stormpx.net.netty.PartialSocketChannel;

public class ChannelFactoryProvider {

    private final DatagramChannelFactory<? extends DatagramChannel> datagramChannelFactory;
    private final ChannelFactory<? extends Channel> socketChannelFactory;
    private final ChannelFactory<? extends ServerChannel> serverChannelChannelFactory;

    public ChannelFactoryProvider(DatagramChannelFactory<? extends DatagramChannel> datagramChannelFactory, ChannelFactory<? extends Channel> socketChannelFactory, ChannelFactory<? extends ServerChannel> serverChannelChannelFactory) {
        this.datagramChannelFactory = datagramChannelFactory;
        this.socketChannelFactory = socketChannelFactory;
        this.serverChannelChannelFactory = serverChannelChannelFactory;
    }

    public static ChannelFactoryProvider ofPartial(){
        return new ChannelFactoryProvider(
                DatagramChannelFactory.newFactory(it->new PartialDatagramChannel(),PartialDatagramChannel::new),
                PartialSocketChannel::new,
                PartialServerSocketChannel::new
        );
    }

    public static ChannelFactoryProvider of(Transport transport){
        return new ChannelFactoryProvider(
                DatagramChannelFactory.newFactory(transport::datagramChannel,transport::datagramChannel),
                transport.channelFactory(false),
                transport.serverChannelFactory(false)
        );
    }

    public DatagramChannelFactory<? extends DatagramChannel> getDatagramChannelFactory() {
        return datagramChannelFactory;
    }

    public ChannelFactory<? extends Channel> getSocketChannelFactory() {
        return socketChannelFactory;
    }

    public ChannelFactory<? extends ServerChannel> getServerChannelChannelFactory() {
        return serverChannelChannelFactory;
    }
}
