package io.crowds.proxy.transport.proxy.wisp;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.TP;
import io.crowds.proxy.common.BaseChannelInitializer;
import io.crowds.proxy.common.IdleTimeoutHandler;
import io.crowds.proxy.transport.Destination;
import io.crowds.proxy.transport.TransportOption;
import io.crowds.proxy.transport.proxy.AbstractProxyTransport;
import io.crowds.proxy.transport.ws.WebsocketTransport;
import io.crowds.proxy.transport.ws.WsOption;
import io.crowds.util.Async;
import io.crowds.util.Strs;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoop;
import io.netty.channel.local.LocalAddress;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * wisp client.
 * <p>
 * the websocket connection to the wisp server is opened by the inherited
 * {@link AbstractProxyTransport#createChannel(EventLoop, NetLocation)} (through {@link WebsocketTransport}),
 * then the channel is wrapped into a single {@link WispConnection}.
 * all the proxied connections are derived from that connection through a local channel pair.
 */
public class WispProxyTransport extends AbstractProxyTransport<WispOption> {
    private final static Logger logger = LoggerFactory.getLogger(WispProxyTransport.class);

    private static final int DEFAULT_UDP_IDLE_SECONDS = 120;

    private final EventLoop eventLoop;
    private final LocalAddress localAddress;
    private final int udpIdleSeconds;

    /**
     * the only wisp connection of this transport, it is accessed on the {@link #eventLoop}.
     * the future covers both the establishing connection and the established one,
     * it is reset once the connection is closed so that the next request will establish a new one.
     */
    private Future<WispConnection> connection;

    public WispProxyTransport(ChannelCreator channelCreator, WispOption wispOption) {
        super(channelCreator, normalize(wispOption));
        this.eventLoop = channelCreator.getEventLoopGroup().next();
        this.udpIdleSeconds = getProtocolOption().getConnIdle() > 0
                ? getProtocolOption().getConnIdle()
                : DEFAULT_UDP_IDLE_SECONDS;
        if (!(transport instanceof WebsocketTransport)) {
            throw new IllegalArgumentException("wisp proxy requires a websocket transport");
        }
        this.localAddress = new LocalAddress("wisp-" + UUID.randomUUID());
        bindLocalChannel(this.localAddress);
    }

    private static WispOption normalize(WispOption option) {
        if (option == null) {
            throw new IllegalArgumentException("wisp option is required");
        }
        if (option.getAddress() == null) {
            throw new IllegalArgumentException("wisp proxy requires the host and port of the wisp server");
        }
        String network = option.getNetwork();
        if (Strs.isBlank(network)) {
            option.setNetwork("ws");
        } else if (!"ws".equalsIgnoreCase(network)) {
            throw new IllegalArgumentException("wisp proxy requires the network option to be ws, but got: " + network);
        }
        TransportOption transport = option.getTransport();
        if (transport == null) {
            transport = new TransportOption();
            option.setTransport(transport);
        }
        WsOption ws = transport.getWs();
        if (ws == null) {
            ws = new WsOption();
            transport.setWs(ws);
        }
        if (ws.getSecProtocol() == null) {
            ws.setSecProtocol("wisp-v2");
        }
        if (ws.getMaxFramePayloadLength() == WsOption.DEFAULT_MAX_FRAME_PAYLOAD_LENGTH) {
            //the wisp server may deliver a large socket chunk as a single DATA packet
            ws.setMaxFramePayloadLength(16 * 1024 * 1024);
        }
        return option;
    }

    /**
     * the websocket connection always points to the wisp server instead of the destination of the proxied connection.
     */
    @Override
    public Destination getRemote(TP tp) {
        return new Destination(NetAddr.of(getProtocolOption().getAddress()), TP.TCP);
    }

    private void bindLocalChannel(LocalAddress address) {
        WispLocalServerChannel serverChannel = new WispLocalServerChannel();
        new ServerBootstrap()
                .group(eventLoop)
                .channelFactory(() -> serverChannel)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        WispStream stream = ((WispLocalChannel) ch).stream();
                        if (stream instanceof WispTcpStream tcpStream) {
                            ch.pipeline().addLast("wisp-tcp", new WispTcpHandler(tcpStream));
                        } else if (stream instanceof WispUdpStream udpStream) {
                            ch.pipeline().addLast("wisp-idle", new IdleTimeoutHandler(udpIdleSeconds, null));
                            ch.pipeline().addLast("wisp-udp", new WispUdpHandler(udpStream));
                        } else {
                            logger.error("wisp: the local channel {} is not bound to any stream", ch);
                            ch.close();
                        }
                    }
                })
                .bind(address);
    }

    private Future<WispConnection> getConnection(NetLocation netLocation) {
        Promise<WispConnection> promise = eventLoop.newPromise();
        if (eventLoop.inEventLoop()) {
            Future<WispConnection> current = this.connection;
            if (current != null) {
                current.addListener(Async.cascade(promise));
                return promise;
            }
            Promise<WispConnection> connecting = eventLoop.newPromise();
            this.connection = connecting;
            connecting.addListener((FutureListener<WispConnection>) f -> {
                if (!f.isSuccess() && this.connection == connecting) {
                    this.connection = null;
                }
            });
            connecting.addListener(Async.cascade(promise));
            openConnection(connecting, netLocation);
        } else {
            eventLoop.execute(()->getConnection(netLocation).addListener(Async.cascade(promise)));
        }

        return promise;
    }

    /**
     * opens the websocket connection through the parent implementation and wraps the resulting channel
     * into the wisp connection.
     */
    private void openConnection(Promise<WispConnection> promise, NetLocation netLocation) {
        try {
            super.createChannel(eventLoop, netLocation)
                 .addListener((FutureListener<Channel>) f -> {
                     if (!f.isSuccess()) {
                         promise.tryFailure(new WispException("unable to connect wisp server "
                                 + getProtocolOption().getAddress(), f.cause()));
                         return;
                     }
                     Channel channel = f.getNow();
                     WispConnection wispConnection = new WispConnection(eventLoop, getProtocolOption(), channel);
                     channel.pipeline().addLast("wispCodec", WispCodec.INSTANCE);
                     channel.pipeline().addLast("wisp", wispConnection);
                     wispConnection.onClosed(() -> {
                         if (this.connection == promise) {
                             this.connection = null;
                         }
                     });
                     wispConnection.readyFuture().addListener((FutureListener<Void>) rf -> {
                         if (rf.isSuccess()) {
                             promise.trySuccess(wispConnection);
                         } else {
                             promise.tryFailure(rf.cause());
                         }
                     });
                 });
        } catch (Exception e) {
            promise.tryFailure(new WispException("unable to connect wisp server " + getProtocolOption().getAddress(), e));
        }
    }

    @Override
    public Future<Channel> createChannel(EventLoop eventLoop, NetLocation netLocation) {
        Promise<Channel> promise = eventLoop.newPromise();
        Async.cascadeFailure(getConnection(netLocation),promise, f->{
            WispConnection connection = f.getNow();
            Async.cascadeFailure(connection.openStream(netLocation),promise,af->{
                openChannel(eventLoop, af.getNow(), promise);
            });

        });
        return promise;
    }

    private void openChannel(EventLoop eventLoop, WispStream stream, Promise<Channel> promise) {
        if (eventLoop.inEventLoop()) {
            WispLocalChannel localChannel = new WispLocalChannel(stream);
            new Bootstrap()
                .group(eventLoop)
                .channelFactory(() -> localChannel)
                .handler(BaseChannelInitializer.EMPTY)
                .connect(localAddress)
                .addListener((FutureListener<Void>) f -> {
                    if (!f.isSuccess()) {
                        stream.onAbort(f.cause());
                        promise.tryFailure(f.cause());
                        return;
                    }
                    promise.trySuccess(localChannel);
                });
        } else {
            eventLoop.execute(()-> openChannel(eventLoop, stream, promise));
        }
    }
}
