package io.crowds.proxy.transport.proxy.wisp;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;

import java.util.List;

/**
 * a wisp protocol packet, one websocket frame always contains exactly one packet.
 */
public sealed interface WispPacket {

    int streamId();

    /**
     * drop the payload of this packet if the packet owns one.
     */
    default void release() {
    }

    /**
     * asks the server to create a new stream.
     */
    record Connect(int streamId, int streamType, String hostname, int port) implements WispPacket {
    }

    /**
     * the payload which is sent to or received from the destination.
     */
    record Data(int streamId, ByteBuf payload) implements WispPacket {
        @Override
        public void release() {
            ReferenceCountUtil.safeRelease(payload);
        }
    }

    /**
     * the buffer size which the server is able to accept.
     */
    record Continue(int streamId, int bufferRemaining) implements WispPacket {
    }

    /**
     * closes the stream (or the whole connection when the stream id is 0).
     */
    record Close(int streamId, int reason) implements WispPacket {
    }

    /**
     * the handshake packet which describes the supported protocol extensions.
     */
    record Info(int streamId, int major, int minor, List<WispExtension> extensions) implements WispPacket {
    }
}
