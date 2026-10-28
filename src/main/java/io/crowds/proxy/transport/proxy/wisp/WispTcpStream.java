package io.crowds.proxy.transport.proxy.wisp;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.TP;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * a single tcp wisp stream.
 * <p>
 * the state of this class is confined to the event loop of the {@link WispConnection}.
 * the accepted (child) local channel is used to read the data of the local client and to pause reading
 * when the send buffer of the server is full, the other side (the channel of the caller) is used to
 * deliver the payload of the destination and to propagate the writability and the close event.
 */
public class WispTcpStream implements WispStream {
    private final static Logger logger = LoggerFactory.getLogger(WispTcpStream.class);

    private static final int MAX_PENDING_PACKETS = 1024;

    private final WispConnection connection;
    private final int id;
    private final NetAddr dst;

    private final Deque<ByteBuf> pending = new ArrayDeque<>();
    private Channel channel;
    private volatile Channel clientChannel;
    private int credit;
    private boolean opened;
    private boolean remoteClosed;
    private volatile boolean writable = true;

    public WispTcpStream(WispConnection connection, int id, NetAddr dst) {
        this.connection = connection;
        this.id = id;
        this.dst = dst;
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public void onWritabilityChanged() {
        if (connection.isWriteable()){
            flush();
        }
        updateWritable();
    }

    void attach(Channel channel, Channel clientChannel) {
        this.channel = channel;
        this.clientChannel = clientChannel;
    }

    /**
     * called once the local channel is active, sends the CONNECT packet to the wisp server.
     */
    void open() {
        if (opened || remoteClosed) {
            return;
        }
        opened = true;
        connection.writeConnect(id, TP.TCP, dst.getHost(), dst.getPort());
        this.credit = connection.initialBuffer();
        updateWritable();
    }

    /**
     * data from the local client which should be sent to the destination server.
     */
    void write(ByteBuf content) {
        if (remoteClosed) {
            content.release();
            return;
        }
        if (credit > 0 && connection.isWriteable()) {
            connection.writeData(id, content);
            credit--;
            updateWritable();
            return;
        }
        if (pending.size() >= MAX_PENDING_PACKETS) {
            content.release();
            logger.error("wisp stream {}: too many pending packets, closing", id);
            onLocalClosed();
            return;
        }
        updateWritable();
        pending.add(content);
    }

    void flush(){
        while (credit > 0 && connection.isWriteable() && !pending.isEmpty()) {
            connection.writeData(id, pending.poll());
            credit--;
        }
    }

    @Override
    public void onData(ByteBuf data) {
        Channel clientChannel = this.clientChannel;
        if (clientChannel == null) {
            data.release();
            return;
        }
        if (clientChannel.eventLoop().inEventLoop()) {
            deliver(clientChannel, data);
        } else {
            clientChannel.eventLoop().execute(() -> deliver(clientChannel, data));
        }
    }

    private void deliver(Channel clientChannel, ByteBuf data) {
        if (!clientChannel.isActive()) {
            data.release();
            return;
        }
        clientChannel.pipeline().fireChannelRead(data);
        clientChannel.pipeline().fireChannelReadComplete();
    }

    @Override
    public void onContinue(int bufferRemaining) {
        if (remoteClosed) {
            return;
        }
        credit = Math.max(0, bufferRemaining);
        flush();
        updateWritable();
    }

    @Override
    public void onRemoteClose(int reason) {
        if (remoteClosed) {
            return;
        }
        remoteClosed = true;
        connection.unregister(id);
        releasePending();
        setWritable(false);
        if (logger.isDebugEnabled()) {
            logger.debug("wisp stream {} closed by the server: {}", id, WispConstants.closeReason(reason));
        }
        Channel clientChannel = this.clientChannel;
        Channel channel = this.channel;
        Runnable task = () -> {
            if (clientChannel != null && clientChannel.isActive()) {
                //emulate a half closure (remote FIN) so that the data which has already been received will not be dropped
                clientChannel.pipeline().fireUserEventTriggered(ChannelInputShutdownReadComplete.INSTANCE);
            }
            if (channel != null) {
                channel.close();
            }
        };
        if (clientChannel != null && !clientChannel.eventLoop().inEventLoop()) {
            clientChannel.eventLoop().execute(task);
        } else {
            task.run();
        }
    }

    @Override
    public void onConnectionClosed(Throwable cause) {
        remoteClosed = true;
        releasePending();
        setWritable(false);
        Channel clientChannel = this.clientChannel;
        Channel channel = this.channel;
        Runnable task = () -> {
            if (clientChannel != null && clientChannel.isActive()) {
                clientChannel.pipeline().fireExceptionCaught(new WispException("wisp connection closed", cause));
            }
            if (channel != null) {
                channel.close();
            }
        };
        if (clientChannel != null && !clientChannel.eventLoop().inEventLoop()) {
            clientChannel.eventLoop().execute(task);
        } else {
            task.run();
        }
    }

    @Override
    public void onAbort(Throwable cause) {
        connection.execute(() -> {
            if (!opened) {
                connection.unregister(id);
            }
            releasePending();
        });
    }

    /**
     * the local channel has been closed.
     */
    void onLocalClosed() {
        if (!remoteClosed) {
            remoteClosed = true;
            connection.writeClose(id, WispConstants.CLOSE_VOLUNTARY);
        }
        connection.unregister(id);
        releasePending();
        setWritable(false);
    }

    @Override
    public boolean isWritable() {
        return writable;
    }

    private void setWritable(boolean value) {
        if (this.writable == value) {
            return;
        }
        this.writable = value;
        Channel clientChannel = this.clientChannel;
        if (clientChannel == null) {
            return;
        }
        clientChannel.pipeline().fireChannelWritabilityChanged();
    }

    private void updateWritable() {
        setWritable(opened && !remoteClosed && credit > 0 && connection.isWriteable());
    }


    private void releasePending() {
        while (!pending.isEmpty()) {
            pending.poll().release();
        }
    }

    @Override
    public String toString() {
        return "WispTcpStream{" + id + ", " + dst + '}';
    }
}
