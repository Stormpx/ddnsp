package io.crowds.proxy.transport.proxy.ssh.sshd;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.DuplexChannel;
import io.netty.util.ReferenceCountUtil;
import org.apache.sshd.common.channel.IoWriteFutureImpl;
import org.apache.sshd.common.io.*;
import org.apache.sshd.common.util.Readable;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.closeable.AbstractCloseable;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChannelDelegateIoSession extends AbstractCloseable implements IoSession {

    private final long id;
    private final Channel channel;
    private final Map<Object,Object> attribute=new ConcurrentHashMap<>();
    private final ChannelDelegateService service;
    private final IoHandler ioHandler;
    private final SocketAddress acceptanceAddress=null;
    private final AtomicBoolean readSuspended = new AtomicBoolean();
    private final DefaultIoConnectFuture connectFuture;
    private ChannelHandlerContext context;

    public ChannelDelegateIoSession(IoHandler ioHandler, ChannelDelegateService service,long id, Channel channel) {
        this.id = id;
        this.channel = channel;
        this.ioHandler=ioHandler;
        this.service = service;
        this.connectFuture=new DefaultIoConnectFuture(channel, null);
        initChannel();
    }

    private void initChannel(){
        channel.pipeline().addLast(new Adapter());
    }

    class Adapter extends ChannelInboundHandlerAdapter{

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            ChannelDelegateIoSession.this.context=ctx;
            service.getManagedSessions().put(id,ChannelDelegateIoSession.this);
            ioHandler.sessionCreated(ChannelDelegateIoSession.this);
            connectFuture.setSession(ChannelDelegateIoSession.this);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            service.getManagedSessions().remove(id);
            ioHandler.sessionClosed(ChannelDelegateIoSession.this);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf buf){
                try {
                    Readable readable = new Readable() {
                        @Override
                        public int available() {
                            return buf.readableBytes();
                        }

                        @Override
                        public void getRawBytes(byte[] data, int offset, int len) {
                            buf.getBytes(0, data, offset, len);
                        }
                    };
                    ioHandler.messageReceived(ChannelDelegateIoSession.this,readable);
                } finally {
                    ReferenceCountUtil.safeRelease(buf);
                }
            }else{
                super.channelRead(ctx,msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            ioHandler.exceptionCaught(ChannelDelegateIoSession.this,cause);
        }
    }

    public DefaultIoConnectFuture getConnectFuture() {
        return connectFuture;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public SocketAddress getAcceptanceAddress() {
        return acceptanceAddress;
    }

    @Override
    public Object getAttribute(Object key) {
        return attribute.get(key);
    }

    @Override
    public Object setAttribute(Object key, Object value) {
        return attribute.put(key,value);
    }

    @Override
    public Object setAttributeIfAbsent(Object key, Object value) {
        return attribute.putIfAbsent(key,value);
    }

    @Override
    public Object removeAttribute(Object key) {
        return attribute.remove(key);
    }

    @Override
    public IoWriteFuture writeBuffer(Buffer buffer) throws IOException {
        if (context==null){
            throw new IOException("ChannelHandlerContext is not exists");
        }
        int available = buffer.available();
        ByteBuf byteBuf = context.alloc().buffer(available);
        byteBuf.writeBytes(buffer.array(), buffer.rpos(), available);
        IoWriteFutureImpl future = new IoWriteFutureImpl(getId(), buffer);
        context.writeAndFlush(byteBuf)
                .addListener(wf->{
                    if (wf.isSuccess()){
                        future.setValue(true);
                    }else{
                        var cause = wf.cause();
                        future.setValue(cause==null?new RuntimeException("writeBuffer failed"):cause);
                    }
                });
        if (future.isDone()){
            Throwable throwable = future.getException();
            if (throwable!=null){
                throw throwable instanceof IOException ioe? ioe : new IOException(throwable);
            }
        }
        return future;
    }

    @Override
    public IoService getService() {
        return service;
    }

    @Override
    public void shutdownOutputStream() throws IOException {
        if (channel instanceof DuplexChannel duplexChannel){
            duplexChannel.shutdownOutput();
        }
    }

    @Override
    public void suspendRead() {
        if (readSuspended.compareAndSet(true,false)){
            channel.config().setAutoRead(false);
        }
    }

    @Override
    public void resumeRead() {
        readSuspended.compareAndSet(false,true);
        channel.config().setAutoRead(true);
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return channel.remoteAddress();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return channel.localAddress();
    }
}
