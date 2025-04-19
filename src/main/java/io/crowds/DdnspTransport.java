package io.crowds;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.vertx.core.transport.Transport;
import org.stormpx.net.PartialNetStack;
import org.stormpx.net.netty.PartialIoHandler;

public class DdnspTransport implements Transport {

    private final Transport transport;
    private final io.vertx.core.spi.transport.Transport implementation;

    public DdnspTransport(PartialNetStack netStack) {
        Transport nativeTransport = Transport.nativeTransport();
        this.transport = nativeTransport==null?Transport.NIO:nativeTransport;
        this.implementation = new io.vertx.core.spi.transport.Transport() {
            final io.vertx.core.spi.transport.Transport delegate = transport.implementation();
            @Override
            public IoHandlerFactory ioHandlerFactory() {
                return PartialIoHandler.newFactory(netStack,delegate.ioHandlerFactory());
            }

            @Override
            public DatagramChannel datagramChannel() {
                return delegate.datagramChannel();
            }

            @Override
            public DatagramChannel datagramChannel(InternetProtocolFamily family) {
                return delegate.datagramChannel(family);
            }

            @Override
            public ChannelFactory<? extends Channel> channelFactory(boolean domainSocket) {
                return delegate.channelFactory(domainSocket);
            }

            @Override
            public ChannelFactory<? extends ServerChannel> serverChannelFactory(boolean domainSocket) {
                return delegate.serverChannelFactory(domainSocket);
            }
        };
    }

    @Override
    public String name() {
        return transport.name();
    }

    @Override
    public boolean available() {
        return transport.available();
    }

    @Override
    public Throwable unavailabilityCause() {
        return transport.unavailabilityCause();
    }

    @Override
    public io.vertx.core.spi.transport.Transport implementation() {
        return implementation;
    }
}
