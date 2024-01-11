package io.crowds.proxy.transport.proxy.ssh.sshd;

import io.crowds.proxy.transport.proxy.ssh.SshSessionContext;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;
import org.apache.sshd.client.channel.ChannelDirectTcpip;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.channel.ChannelAsyncOutputStream;
import org.apache.sshd.common.channel.LocalWindow;
import org.apache.sshd.common.io.IoWriteFuture;
import org.apache.sshd.common.util.Readable;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.common.util.threads.ThreadUtils;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;

public class ChannelDelegateDirectTcpip extends ChannelDirectTcpip {

    private final SshSessionContext context;
    private final Queue<Buffer> pendingBuffers;
    private Consumer<ByteBuf> bufferHandler;
    private IoWriteFuture ioWriteFuture;


    public ChannelDelegateDirectTcpip(SshdSocketAddress remote, SshSessionContext context) {
        super(SshdSocketAddress.LOCALHOST_ADDRESS, remote);
        this.context = context;
        this.pendingBuffers= PlatformDependent.newSpscQueue();
    }

    public Future<Void> openChannel(){
        Promise<Void> promise = context.eventLoop().newPromise();
        try {
            open().addListener(f->{
                if (!f.isOpened()){
                    promise.tryFailure(f.getException());
                }else{
                    promise.trySuccess(null);
                }
            });
        } catch (IOException e) {
            promise.tryFailure(e);
        }
        return promise;
    }

    private void quietlyClose(){
        try {
            this.pendingBuffers.clear();
            close();
        } catch (IOException e) {

        }
    }

    private void doWriteBuffer(Buffer buffer){
        try {
            if (this.ioWriteFuture==null||this.ioWriteFuture.isWritten()){
                buffer=buffer!=null?buffer:pendingBuffers.poll();
                if (buffer==null){
                    return;
                }
                Buffer finalBuffer = buffer;
                this.ioWriteFuture = ThreadUtils.runAsInternal(()->asyncIn.writeBuffer(finalBuffer));
                this.ioWriteFuture.addListener(iwf->{
                   if (!iwf.isWritten()){
                       quietlyClose();
                   }else{
                       doWriteBuffer(null);
                   }
                });
            }else{
                this.pendingBuffers.add(buffer);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void writeBuffer(ByteBuf buf){
        Objects.requireNonNull(buf);
        if (openFuture==null||!openFuture.isOpened()){
            throw new IllegalStateException("the ssh direct tcp channel is not open.");
        }
        if (isClosed()){
            return;
        }

        ByteArrayBuffer buffer = new ByteArrayBuffer(buf.readableBytes());
        buffer.putBuffer(new Readable() {
            @Override
            public int available() {
                return buf.readableBytes();
            }

            @Override
            public void getRawBytes(byte[] data, int offset, int len) {
                buf.getBytes(0, data, offset, len);
            }
        });
        doWriteBuffer(buffer);
    }

    public ChannelDelegateDirectTcpip bufferHandler(Consumer<ByteBuf> bufferHandler) {
        this.bufferHandler = bufferHandler;
        return this;
    }

    @Override
    protected void doOpen() throws IOException {
        //do nothing
        asyncIn = new ChannelAsyncOutputStream(this, SshConstants.SSH_MSG_CHANNEL_DATA);
    }


    @Override
    protected void doWriteData(byte[] data, int off, long len) throws IOException {
        ByteBuf buf = context.server.alloc().buffer((int) len);
        buf.writeBytes(data,off, (int) len);
        if (this.bufferHandler!=null){
            this.bufferHandler.accept(buf);
        }
        LocalWindow wLocal = getLocalWindow();
        wLocal.release(len);
    }
}

