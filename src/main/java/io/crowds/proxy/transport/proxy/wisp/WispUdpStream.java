package io.crowds.proxy.transport.proxy.wisp;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.TP;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * a single udp wisp stream which is bound to one destination address.
 * <p>
 * the state of this class is confined to the event loop of the {@link WispConnection}.
 * the accepted (child) local channel is used to read the datagrams of the local client
 * and to deliver the datagrams of the destination.
 */
public class WispUdpStream implements WispStream {
    private final static Logger logger = LoggerFactory.getLogger(WispUdpStream.class);

    private final WispConnection connection;
    private final int id;
    private final NetAddr src;
    private final NetAddr dst;

    private Channel channel;
    private boolean opened;
    private boolean closed;

    public WispUdpStream(WispConnection connection, int id, NetAddr src, NetAddr dst) {
        this.connection = connection;
        this.id = id;
        this.src = src;
        this.dst = dst;
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public void onWritabilityChanged() {

    }

    public NetAddr destination() {
        return dst;
    }

    void attach(Channel channel) {
        this.channel = channel;
    }

    /**
     * called once the local channel is active, sends the CONNECT packet to the wisp server.
     */
    void open() {
        if (opened || closed) {
            return;
        }
        opened = true;
        connection.writeConnect(id, TP.UDP, dst.getHost(), dst.getPort());
    }

    /**
     * a datagram from the local client which should be sent to the destination.
     */
    void write(DatagramPacket packet) {
        if (!opened || closed || !connection.isWriteable()) {
            return;
        }
        ByteBuf content = packet.content().retain();
        if (content.isReadable()) {
            connection.writeData(id, content);
        } else {
            content.release();
        }
    }

    @Override
    public void onData(ByteBuf data) {
        Channel channel = this.channel;
        if (closed || channel == null || !channel.isActive()) {
            data.release();
            return;
        }
        channel.writeAndFlush(new DatagramPacket(data, src.getAsInetAddr(), dst.getAsInetAddr()));
    }

    @Override
    public void onContinue(int bufferRemaining) {
        //udp streams have no buffering
    }

    @Override
    public void onRemoteClose(int reason) {
        if (closed) {
            return;
        }
        closed = true;
        connection.unregister(id);
        if (logger.isDebugEnabled()) {
            logger.debug("wisp udp stream {} ({}:{}) closed by the server: {}", id,
                    dst.getHost(), dst.getPort(), WispConstants.closeReason(reason));
        }
        closeChannel();
    }

    @Override
    public void onConnectionClosed(Throwable cause) {
        closed = true;
        closeChannel();
    }

    @Override
    public void onAbort(Throwable cause) {
        connection.execute(() -> {
            if (!opened) {
                connection.unregister(id);
            }
        });
    }

    /**
     * the local channel has been closed.
     */
    void onLocalClosed() {
        if (!closed) {
            closed = true;
            connection.writeClose(id, WispConstants.CLOSE_VOLUNTARY);
        }
        connection.unregister(id);
    }

    @Override
    public boolean isWritable() {
        return !closed;
    }

    private void closeChannel() {
        Channel channel = this.channel;
        if (channel != null) {
            channel.close();
        }
    }

    @Override
    public String toString() {
        return "WispUdpStream{" + id + ", " + dst + '}';
    }
}
