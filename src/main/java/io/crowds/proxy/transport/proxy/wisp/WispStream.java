package io.crowds.proxy.transport.proxy.wisp;

import io.netty.buffer.ByteBuf;

/**
 * a wisp stream. it is attached to the local channel of the proxied connection
 * and registered in the stream table of the {@link WispConnection}.
 */
public interface WispStream {

    int id();

    /**
     * whether the local channel is able to accept more data.
     */
    boolean isWritable();

    /**
     * the writability of the websocket channel has been changed.
     */
    void onWritabilityChanged();

    /**
     * the payload comes from the destination server.
     */
    void onData(ByteBuf data);

    void onContinue(int bufferRemaining);

    void onRemoteClose(int reason);

    void onConnectionClosed(Throwable cause);

    /**
     * the local channel could not be created.
     */
    void onAbort(Throwable cause);
}
