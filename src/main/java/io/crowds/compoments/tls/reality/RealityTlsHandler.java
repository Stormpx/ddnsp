package io.crowds.compoments.tls.reality;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Netty bridge for the REALITY handshake. Drives the non blocking BouncyCastle client protocol on the
 * channel event loop and behaves like a record layer for the handlers above it.
 */
public final class RealityTlsHandler extends ChannelDuplexHandler {

    private static final Logger logger = LoggerFactory.getLogger(RealityTlsHandler.class);
    private static final int HANDSHAKE_TIMEOUT_SECONDS = 10;
    private static final int MIN_SCRATCH_SIZE = 4096;

    private static final int HANDSHAKE_WAITING = 0;
    private static final int HANDSHAKE_PROCESSING = 1;
    private static final int HANDSHAKE_FINISHED = 2;
    private static final int HANDSHAKE_ERROR = 3;

    private final RealityOption option;
    private final String serverName;
    private final List<String> alpn;
    private final Deque<PendingWrite> pendingWrites = new ArrayDeque<>(4);

    private RealitySession session;
    private RealityClientProtocol protocol;
    private ScheduledFuture<?> timeoutFuture;
    private int handshake;
    private boolean closed;
    private boolean flushPending;
    private byte[] cipherTextScratch;
    private byte[] applicationDataScratch;

    public RealityTlsHandler(RealityOption option, String serverName, List<String> alpn) {
        this.option = option;
        this.serverName = serverName;
        this.alpn = alpn;
    }

    /**
     * Negotiated protocol version, or null while the handshake is still in progress.
     */
    public String protocol() {
        return handshake == HANDSHAKE_FINISHED ? "TLSv1.3" : null;
    }

    public boolean isAuthenticated() {
        return session != null && session.isAuthenticated();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        startHandshake(ctx);
    }

    private void startHandshake(ChannelHandlerContext ctx) {
        if (handshake == HANDSHAKE_WAITING){
            handshake = HANDSHAKE_PROCESSING;
            try {
                session = new RealitySession(option);
                protocol = new RealityClientProtocol(session);
                protocol.connect(new RealityTlsClient(session, serverName, alpn));
                writeOutput(ctx);
                ctx.flush();
                timeoutFuture = ctx.executor().schedule(
                    () -> failHandshake(ctx, new SSLHandshakeException("REALITY handshake timed out")),
                    HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            } catch (Throwable e) {
                failHandshake(ctx, e);
            }
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf buf)) {
            ctx.fireChannelRead(msg);
            return;
        }
        if (closed) {
            buf.release();
            return;
        }
        try {
            offerCipherText(buf);
            readPlaintext(ctx);
            writeOutput(ctx);
            completeHandshakeIfDone(ctx);
        } catch (Throwable e) {
            failHandshake(ctx, e);
        } finally {
            buf.release();
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if (flushPending) {
            flushPending = false;
            ctx.flush();
        }
        super.channelReadComplete(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (!(msg instanceof ByteBuf buf)) {
            ctx.write(msg, promise);
            return;
        }
        if (closed) {
            buf.release();
            promise.tryFailure(new SSLHandshakeException("REALITY connection is closed"));
            return;
        }
        if (handshake < HANDSHAKE_FINISHED) {
            pendingWrites.addLast(new PendingWrite(buf, promise));
            return;
        }
        try {
            writeApplicationData(ctx, buf, promise);
        } catch (Throwable e) {
            buf.release();
            promise.tryFailure(e);
            failHandshake(ctx, e);
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        if (!closed && protocol != null) {
            try {
                writeOutput(ctx);
                flushPending = false;
            } catch (Throwable e) {
                failHandshake(ctx, e);
                return;
            }
        }
        ctx.flush();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        cleanup();
        super.channelInactive(ctx);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        boolean active = ctx.channel().isActive();
        if (active){
            startHandshake(ctx);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        cleanup();
        super.handlerRemoved(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        failHandshake(ctx, cause);
    }

    private void writeApplicationData(ChannelHandlerContext ctx, ByteBuf buf, ChannelPromise promise) throws IOException {
        int length = buf.readableBytes();
        if (length > 0) {
            if (buf.hasArray()) {
                protocol.writeApplicationData(buf.array(), buf.arrayOffset() + buf.readerIndex(), length);
            } else {
                applicationDataScratch = copyToScratch(buf, applicationDataScratch);
                protocol.writeApplicationData(applicationDataScratch, 0, length);
            }
        }
        buf.release();
        writeOutput(ctx);
        flushPending = true;
        promise.trySuccess();
    }

    /**
     * Delivers everything the protocol produced, both during and after the handshake.
     */
    private void writeOutput(ChannelHandlerContext ctx) throws IOException {
        int available;
        while ((available = protocol.getAvailableOutputBytes()) > 0) {
            ByteBuf cipherText = ctx.alloc().ioBuffer(available);
            ByteBuffer buffer = cipherText.nioBuffer(0, available);
            int read = protocol.readOutput(buffer, available);
            if (read <= 0) {
                cipherText.release();
                break;
            }
            cipherText.writerIndex(read);
            ctx.write(cipherText);
            flushPending = true;
        }
    }

    private void readPlaintext(ChannelHandlerContext ctx) {
        int available;
        while ((available = protocol.getAvailableInputBytes()) > 0) {
            ByteBuf plain = ctx.alloc().ioBuffer(available);
            ByteBuffer buffer = plain.nioBuffer(0, available);
            int read = protocol.readInput(buffer, available);
            if (read <= 0) {
                plain.release();
                break;
            }
            plain.writerIndex(read);
            ctx.fireChannelRead(plain);
        }
    }

    /**
     * Feeds received records to the protocol. The protocol only accepts byte arrays here, so array
     * backed buffers are passed as is and direct ones go through a reusable scratch array.
     */
    private void offerCipherText(ByteBuf buf) throws IOException {
        int length = buf.readableBytes();
        if (length == 0) {
            return;
        }
        if (buf.hasArray()) {
            protocol.offerInput(buf.array(), buf.arrayOffset() + buf.readerIndex(), length);
        } else {
            cipherTextScratch = copyToScratch(buf, cipherTextScratch);
            protocol.offerInput(cipherTextScratch, 0, length);
        }
    }

    private static byte[] copyToScratch(ByteBuf buf, byte[] scratch) {
        int length = buf.readableBytes();
        if (scratch == null || scratch.length < length) {
            scratch = new byte[Math.max(length, MIN_SCRATCH_SIZE)];
        }
        buf.getBytes(buf.readerIndex(), scratch, 0, length);
        return scratch;
    }

    private void completeHandshakeIfDone(ChannelHandlerContext ctx) throws IOException {
        if (handshake == HANDSHAKE_FINISHED || protocol.isHandshaking()) {
            return;
        }
        if (protocol.isClosed() || protocol.isFailed()) {
            throw new SSLHandshakeException("REALITY handshake failed");
        }
        handshake = HANDSHAKE_FINISHED;
        cancelTimeout();
        flushPendingWrites(ctx);
        writeOutput(ctx);
        ctx.fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS);
    }

    private void flushPendingWrites(ChannelHandlerContext ctx) throws IOException {
        PendingWrite pendingWrite;
        while ((pendingWrite = pendingWrites.pollFirst()) != null) {
            ByteBuf buf = pendingWrite.buf();
            if (!buf.isReadable()) {
                buf.release();
                continue;
            }
            writeApplicationData(ctx, buf, pendingWrite.promise());
        }
    }

    private void failHandshake(ChannelHandlerContext ctx, Throwable cause) {
        if (closed) {
            return;
        }
        closed = true;
        handshake = HANDSHAKE_ERROR;
        cancelTimeout();
        releasePendingWrites();
        if (cause instanceof RealityAuthException) {
            logger.warn(cause.getMessage());
        }
        ctx.fireExceptionCaught(cause);
        ctx.close();
    }

    private void cleanup() {
        closed = true;
        cancelTimeout();
        releasePendingWrites();
        if (protocol != null) {
            try {
                protocol.close();
            } catch (Throwable e) {
                logger.debug("unable to close REALITY protocol", e);
            }
        }
        if (session != null && session.crypto().x25519() != null) {
            session.crypto().x25519().destroy();
        }
    }

    private void releasePendingWrites() {
        PendingWrite pendingWrite;
        while ((pendingWrite = pendingWrites.pollFirst()) != null) {
            pendingWrite.buf().release();
            pendingWrite.promise().tryFailure(new SSLHandshakeException("REALITY handshake failed"));
        }
    }

    private void cancelTimeout() {
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
            timeoutFuture = null;
        }
    }

    private record PendingWrite(ByteBuf buf, ChannelPromise promise) {
    }
}
