package io.crowds.proxy.transport.proxy.ssh;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.transport.proxy.ssh.sshd.ChannelDelegateDirectTcpip;
import io.crowds.util.Async;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalChannel;
import io.netty.util.concurrent.Promise;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.session.ConnectionService;
import org.apache.sshd.common.util.net.SshdSocketAddress;

import java.io.IOException;

public class SshSessionContext {
    private final EventLoop eventLoop;
    private final NetAddr dst;
    private final Promise<Void> openPromise;

    public LocalChannel client;
    public LocalChannel server;
    public SshSessionContext(EventLoop eventLoop,NetAddr dst) {
        this.eventLoop=eventLoop;
        this.dst = dst;
        this.openPromise =eventLoop.newPromise();
    }
    public EventLoop eventLoop(){
        return eventLoop;
    }


    public SshSessionContext setClientChannel(LocalChannel client) {
        this.client = client;
        return this;
    }

    public void setServerChannel(ClientSession session, LocalChannel localChannel) throws IOException {
        //the sub server channel
        this.server=localChannel;
        ConnectionService service = session.getService(ConnectionService.class);
        ChannelDelegateDirectTcpip tcpip = new ChannelDelegateDirectTcpip(new SshdSocketAddress(dst.getHost(), dst.getPort()), this);
        service.registerChannel(tcpip);
        this.server.pipeline()
                   .addLast(new SimpleChannelInboundHandler<>() {
                       @Override
                       protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                           if (msg instanceof ByteBuf buf) {
                               //data from client. write to dst
                               tcpip.writeBuffer(buf);
                           }
                       }
                   });
        tcpip.bufferHandler(buf->server.writeAndFlush(buf));
        tcpip.addCloseFutureListener(_-> server.close());

        tcpip.openChannel().addListener(Async.cascade(openPromise));
    }


    public Promise<Void> openFuture() {
        return openPromise;
    }


    public void setAutoRead(boolean autoRead) {
        this.client.config().setAutoRead(autoRead);
        this.client.pipeline().fireChannelWritabilityChanged();
    }
}
