package io.crowds.proxy.transport.proxy.wisp;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.TP;
import io.crowds.util.Strs;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoop;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static io.crowds.proxy.transport.proxy.wisp.WispConstants.*;

/**
 * a single wisp connection. all streams ({@link WispTcpStream} / {@link WispUdpStream}) are multiplexed over it.
 * <p>
 * it is also the handler of the websocket channel which decodes the {@link WispPacket}s of {@link WispCodec}.
 * all the state of this class is confined to the {@link #eventLoop}.
 */
public class WispConnection extends ChannelInboundHandlerAdapter {
    private final static Logger logger = LoggerFactory.getLogger(WispConnection.class);

    private static final long HANDSHAKE_TIMEOUT_SECONDS = 10;

    private enum State {
        HANDSHAKING,
        READY,
        CLOSED
    }

    private final EventLoop eventLoop;
    private final Channel channel;
    private final WispOption option;
    private final Promise<Void> readyPromise;
    private final Map<Integer, WispStream> streams = new HashMap<>();

    private State state = State.HANDSHAKING;
    private ScheduledFuture<?> handshakeTimeout;
    private Runnable closeListener;
    private WispKeyAuth keyAuth;
    private int version;
    private int initialBuffer = 1;
    private boolean serverUdpSupported = true;
    private boolean streamOpenConfirmation = true;
    private boolean udpUnsupportedWarned;
    private int nextStreamId = 1;

    /**
     * called once the websocket channel of this connection is ready to be used.
     * the handshake of the wisp protocol starts with the first packet which is sent by the server.
     */
    public WispConnection(EventLoop eventLoop, WispOption option, Channel channel) {
        this.eventLoop = eventLoop;
        this.option = option;
        this.channel = channel;
        this.readyPromise = eventLoop.newPromise();
        this.state = State.HANDSHAKING;
        this.handshakeTimeout = eventLoop.schedule(this::onHandshakeTimeout,
                HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public Future<Void> readyFuture() {
        return readyPromise;
    }

    public int initialBuffer() {
        return initialBuffer;
    }

    public WispConnection onClosed(Runnable closeListener) {
        this.closeListener = closeListener;
        return this;
    }

    private void onHandshakeTimeout() {
        if (state != State.HANDSHAKING) {
            return;
        }
        fail(new WispException("wisp handshake timeout"));
    }

    public boolean isWriteable(){
        return channel.isWritable();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        boolean writable = ctx.channel().isWritable();
        if (!writable){
            ctx.flush();
            if (ctx.channel().isWritable()){
                return;
            }
        }
        for (WispStream stream : streams.values()) {
            stream.onWritabilityChanged();
        }

        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof WispPacket packet)) {
            ReferenceCountUtil.safeRelease(msg);
            return;
        }
        switch (packet) {
            case WispPacket.Info info -> onInfo(info);
            case WispPacket.Continue cont -> onContinue(cont);
            case WispPacket.Close close -> onClose(close);
            case WispPacket.Data data -> onData(data);
            case WispPacket.Connect connect -> {
                logger.warn("wisp: unexpected CONNECT packet from the server, stream {}", connect.streamId());
            }
        }
    }

    private void onInfo(WispPacket.Info info) {
        if (state != State.HANDSHAKING || info.streamId() != 0) {
            logger.warn("wisp: unexpected INFO packet, state={}, stream={}", state, info.streamId());
            return;
        }
        if (version != 0) {
            fail(new WispException("received a duplicated INFO packet"));
            return;
        }
        boolean udp = false;
        boolean openConfirmation = false;
        boolean passwordSupported = false;
        boolean passwordRequired = false;
        boolean keySupported = false;
        boolean keyRequired = false;
        int algorithms = 0;
        byte[] challenge = null;
        String motd = null;
        for (WispExtension extension : info.extensions()) {
            switch (extension) {
                case WispExtension.Udp u -> udp = true;
                case WispExtension.StreamOpenConfirmation confirmation -> openConfirmation = true;
                case WispExtension.Motd m -> motd = m.message();
                case WispExtension.PasswordAuth passwordAuth -> {
                    passwordSupported = true;
                    passwordRequired = passwordAuth.required();
                }
                case WispExtension.KeyAuth keyAuth -> {
                    keySupported = true;
                    keyRequired = keyAuth.required();
                    algorithms = keyAuth.algorithms();
                    challenge = keyAuth.challenge();
                }
                default -> {
                    //the server must not send the client credentials but be tolerant
                }
            }
        }

        version = 2;
        serverUdpSupported = udp;
        streamOpenConfirmation = openConfirmation;
        logger.info("wisp server version {}.{}, udp supported: {}, stream open confirmation: {}",
                info.major(), info.minor(), udp, openConfirmation);
        if (!Strs.isBlank(motd)) {
            logger.info("wisp server motd: {}", motd);
        }

        boolean useKey = keySupported && WispKeyAuth.hasKey(option);
        if (useKey) {
            if (challenge == null || challenge.length == 0 || (algorithms & SIGN_ALGORITHM_ED25519) == 0) {
                if (keyRequired) {
                    fail(new WispException("wisp server requires public/private key authentication, but no supported signature algorithm was offered"));
                    return;
                }
                useKey = false;
            }
        }
        boolean usePassword = !useKey && passwordSupported && !Strs.isBlank(option.getPassword());
        if (!useKey && !usePassword && (keyRequired || passwordRequired)) {
            fail(new WispException("wisp server requires authentication, but no credentials were configured"));
            return;
        }
        if (keyRequired && !useKey) {
            fail(new WispException("wisp server requires public/private key authentication"));
            return;
        }
        if (passwordRequired && !usePassword) {
            fail(new WispException("wisp server requires password authentication"));
            return;
        }

        List<WispExtension> extensions = new ArrayList<>();
        extensions.add(new WispExtension.Udp());
        extensions.add(new WispExtension.Motd(null));
        extensions.add(new WispExtension.StreamOpenConfirmation());
        if (useKey) {
            WispKeyAuth wispKeyAuth = keyAuth();
            extensions.add(new WispExtension.KeyAuthCredentials(
                    option.getUsername(),
                    SIGN_ALGORITHM_ED25519,
                    wispKeyAuth.publicKeyHash(),
                    wispKeyAuth.sign(challenge)));
        } else if (usePassword) {
            extensions.add(new WispExtension.PasswordAuthCredentials(option.getUsername(), option.getPassword()));
        }
        write(new WispPacket.Info(0, WISP_VERSION_MAJOR, WISP_VERSION_MINOR, extensions));
    }

    private void onContinue(WispPacket.Continue cont) {
        if (state == State.HANDSHAKING) {
            if (cont.streamId() != 0) {
                logger.warn("wisp handshake: unexpected CONTINUE packet of stream {}", cont.streamId());
                return;
            }
            if (version == 0) {
                //a wisp v1 server sends the initial CONTINUE packet immediately after the handshake
                version = 1;
            }
            initialBuffer = Math.max(1, cont.bufferRemaining());
            completeHandshake();
            return;
        }
        if (state != State.READY) {
            return;
        }
        WispStream stream = streams.get(cont.streamId());
        if (stream == null) {
            logger.debug("wisp: ignoring CONTINUE packet of an unknown stream {}", cont.streamId());
            return;
        }
        stream.onContinue(cont.bufferRemaining());
    }

    private void onClose(WispPacket.Close close) {
        int reason = close.reason();
        if (close.streamId() == 0) {
            if (state == State.HANDSHAKING) {
                fail(new WispException("wisp server refused the handshake: " + closeReason(reason)));
            } else if (state == State.READY) {
                fail(new WispException("wisp connection closed by the server: " + closeReason(reason)));
            }
            return;
        }
        if (state != State.READY) {
            logger.warn("wisp: unexpected CLOSE packet of stream {}", close.streamId());
            return;
        }
        WispStream stream = streams.get(close.streamId());
        if (stream == null) {
            logger.debug("wisp: ignoring CLOSE packet of an unknown stream {}", close.streamId());
            return;
        }
        stream.onRemoteClose(reason);
    }

    private void onData(WispPacket.Data data) {
        if (state != State.READY) {
            data.release();
            logger.warn("wisp: unexpected DATA packet, state={}", state);
            return;
        }
        WispStream stream = streams.get(data.streamId());
        if (stream == null || !data.payload().isReadable()) {
            data.release();
            return;
        }
        stream.onData(data.payload());
    }

    private WispKeyAuth keyAuth() {
        if (keyAuth == null) {
            keyAuth = WispKeyAuth.load(option);
        }
        return keyAuth;
    }

    private void completeHandshake() {
        if (state != State.HANDSHAKING) {
            return;
        }
        if (handshakeTimeout != null) {
            handshakeTimeout.cancel(false);
            handshakeTimeout = null;
        }
        state = State.READY;
        logger.debug("wisp connection established, version={}, initialBuffer={}", version, initialBuffer);
        readyPromise.trySuccess(null);
    }

    //----------------------------------------------------------------------------------------- streams

    public Future<WispStream> openStream(NetLocation netLocation) {
        Promise<WispStream> promise = eventLoop.newPromise();
        execute(() -> {
            if (state != State.READY) {
                promise.tryFailure(new WispException("wisp connection is not ready"));
                return;
            }
            int streamId = nextStreamId();
            NetAddr src = netLocation.getSrc();
            NetAddr dst = netLocation.getDst();
            if (netLocation.getTp() == TP.UDP) {
                if (version == 2 && !serverUdpSupported && !udpUnsupportedWarned) {
                    udpUnsupportedWarned = true;
                    logger.warn("wisp server {} does not support udp streams, udp traffic will probably be dropped by the server",
                            option.getAddress());
                }
                WispUdpStream stream = new WispUdpStream(this, streamId, src, dst);
                streams.put(streamId, stream);
                promise.trySuccess(stream);
            } else {
                WispTcpStream stream = new WispTcpStream(this, streamId, dst);
                streams.put(streamId, stream);
                promise.trySuccess(stream);
            }
        });
        return promise;
    }

    int nextStreamId() {
        int streamId = nextStreamId++;
        if (nextStreamId <= 0) {
            //skip the reserved stream id 0
            nextStreamId = 1;
        }
        return streamId;
    }

    void unregister(int streamId) {
        streams.remove(streamId);
    }

    void writeConnect(int streamId, TP tp, String host, int port) {
        int streamType = tp == TP.UDP ? STREAM_TYPE_UDP : STREAM_TYPE_TCP;
        write(new WispPacket.Connect(streamId, streamType, host, port));
    }

    void writeData(int streamId, ByteBuf content) {
        if (!content.isReadable()) {
            content.release();
            return;
        }
        write(new WispPacket.Data(streamId, content));
    }

    void writeClose(int streamId, int reason) {
        if (state == State.CLOSED) {
            return;
        }
        write(new WispPacket.Close(streamId, reason));
    }

    private void write(WispPacket packet) {
        Channel channel = this.channel;
        if (channel == null || !channel.isActive()) {
            packet.release();
            return;
        }
        channel.writeAndFlush(packet).addListener(f -> {
            if (!f.isSuccess()) {
                fail(new WispException("unable to write wisp packet", f.cause()));
            }
        });
    }

    public void execute(Runnable task) {
        if (eventLoop.inEventLoop()) {
            task.run();
        } else {
            eventLoop.execute(task);
        }
    }

    //----------------------------------------------------------------------------------------- lifecycle

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (state != State.CLOSED) {
            fail(new WispException("wisp connection closed unexpectedly"));
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        fail(cause);
        ctx.close();
    }

    private void fail(Throwable cause) {
        if (state == State.CLOSED) {
            return;
        }
        State prevState = state;
        state = State.CLOSED;
        if (handshakeTimeout != null) {
            handshakeTimeout.cancel(false);
            handshakeTimeout = null;
        }
        if (prevState == State.READY) {
            logger.warn("wisp connection closed: {}", Objects.toString(cause.getMessage(), cause.getClass().getName()));
        } else {
            logger.warn("wisp connection failed: {}", Objects.toString(cause.getMessage(), cause.getClass().getName()));
        }
        readyPromise.tryFailure(cause);
        Channel channel = this.channel;
        if (channel != null) {
            channel.close();
        }
        List<WispStream> closedStreams = new ArrayList<>(streams.values());
        streams.clear();
        for (WispStream stream : closedStreams) {
            try {
                stream.onConnectionClosed(cause);
            } catch (Throwable e) {
                logger.warn("wisp: failed to close a stream", e);
            }
        }
        Runnable closeListener = this.closeListener;
        if (closeListener != null) {
            try {
                closeListener.run();
            } catch (Throwable e) {
                logger.warn("wisp: close listener error", e);
            }
        }
    }

    @Override
    public String toString() {
        return "WispConnection{" + option.getAddress() + ", version=" + version + ", state=" + state + '}';
    }
}
