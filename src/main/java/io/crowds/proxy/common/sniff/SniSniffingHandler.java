package io.crowds.proxy.common.sniff;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.AbstractSniHandler;
import io.netty.handler.ssl.SslHandshakeTimeoutException;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class SniSniffingHandler extends AbstractSniHandler<Void> {

    private static final Logger logger = LoggerFactory.getLogger(SniSniffingHandler.class);
    private final long handshakeTimeoutMillis;
    private ScheduledFuture<?> timeoutFuture;
    private final Promise<String> promise;
    private final boolean autoRead;

    private SniSniffingHandler(long handshakeTimeoutMillis, Promise<String> promise, boolean autoRead) {
        super(0);
        this.handshakeTimeoutMillis = handshakeTimeoutMillis;
        this.promise = promise;
        this.autoRead = autoRead;
    }

    public static Future<String> sniffHostname(Channel channel, long handshakeTimeoutMillis) {
        Promise<String> promise = channel.eventLoop().newPromise();
        channel.pipeline().addLast(new SniSniffingHandler(handshakeTimeoutMillis,promise,channel.config().isAutoRead()));
        channel.config().setAutoRead(true);
        return promise;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isActive()) {
            checkStartTimeout(ctx);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelActive();
        checkStartTimeout(ctx);
    }

    private void cleanup(ChannelHandlerContext ctx){
        ctx.pipeline().remove(SniSniffingHandler.this);

    }

    private void checkStartTimeout(final ChannelHandlerContext ctx) {
        if (handshakeTimeoutMillis <= 0 || timeoutFuture != null) {
            return;
        }
        timeoutFuture = ctx.executor().schedule(new Runnable() {
            @Override
            public void run() {
                SslHandshakeTimeoutException exception = new SslHandshakeTimeoutException(
                        "sni sniffing timed out after " + handshakeTimeoutMillis + "ms");
                if (!autoRead){
                    ctx.channel().config().setAutoRead(false);
                }
                promise.tryFailure(exception);
                logger.debug("SNI sniffing timed out");
                if (ctx.channel().isActive()) {
                    cleanup(ctx);
                }
            }
        }, handshakeTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    protected Future<Void> lookup(ChannelHandlerContext ctx, String hostname) throws Exception {
        return ctx.channel().eventLoop().newSucceededFuture(null);
    }

    @Override
    protected void onLookupComplete(ChannelHandlerContext ctx, String hostname, Future<Void> future) throws Exception {
        if (timeoutFuture!=null){
            timeoutFuture.cancel(false);
            timeoutFuture = null;
        }
        try {
            if (!autoRead){
                ctx.channel().config().setAutoRead(false);
            }
            if (hostname==null){
                logger.debug("SNI Hostname is null");
                promise.tryFailure(new DecoderException("SNI Hostname is null"));
                return;
            }
            if (logger.isDebugEnabled())
                logger.debug("hostname: {} detected",hostname);
            promise.trySuccess(hostname);
        }finally {
            cleanup(ctx);
        }


    }
}
