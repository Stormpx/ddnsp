package io.crowds.proxy.transport.proxy.wisp;

import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * creates the accepted child channel of a stream, the wisp stream is taken from the connecting client channel.
 */
public class WispLocalServerChannel extends LocalServerChannel {
    private final static Logger logger = LoggerFactory.getLogger(WispLocalServerChannel.class);

    @Override
    protected LocalChannel newLocalChannel(LocalChannel peer) {
        WispStream stream = ((WispLocalChannel) peer).stream();
        if (stream == null) {
            logger.error("wisp: the local channel {} is not bound to any wisp stream", peer);
        }
        return new WispLocalChannel(this, peer, stream);
    }
}
