package io.crowds.proxy.transport.proxy.wisp;

import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;

/**
 * a local channel which reflects the writability of the derived wisp stream.
 */
public class WispLocalChannel extends LocalChannel {
    private final WispStream stream;
    private final LocalChannel peer;

    public WispLocalChannel(WispStream stream) {
        super();
        this.stream = stream;
        this.peer = null;
    }

    protected WispLocalChannel(LocalServerChannel parent, LocalChannel peer, WispStream stream) {
        super(parent, peer);
        this.stream = stream;
        this.peer = peer;
    }

    public WispStream stream() {
        return stream;
    }

    /**
     * the channel of the other side, only available on the accepted child channel.
     */
    public LocalChannel peer() {
        return peer;
    }

    @Override
    public boolean isWritable() {
        WispStream stream = this.stream;
        return super.isWritable() && (stream == null || stream.isWritable());
    }
}
