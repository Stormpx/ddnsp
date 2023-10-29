package io.crowds.proxy.transport.proxy.ssh;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.common.BaseChannelInitializer;
import io.crowds.util.Async;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.apache.sshd.client.session.ClientSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SshSession {
    private  Channel parent;
    private  ClientSession clientSession;
    private  ChannelGroup channelGroup;
    private final LocalAddress localServer =new LocalAddress(UUID.randomUUID().toString());
    private LocalServerChannel localServerChannel;
    private final Map<LocalAddress, SshSessionContext> contexts = new HashMap<>();
    public SshSession(Channel parent,ClientSession clientSession) {
        this.parent=parent;
        this.clientSession = clientSession;
        this.channelGroup = new DefaultChannelGroup(parent.eventLoop());
        clientSession.addCloseFutureListener(it -> close());
        parent.pipeline()
              .addLast(new ChannelInboundHandlerAdapter(){
                    @Override
                    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
                        for (SshSessionContext context : contexts.values()) {
                            context.setAutoRead(ctx.channel().config().isAutoRead());
                        }
                        super.channelWritabilityChanged(ctx);
                    }
              });
    }

    private void close(){
        if (this.localServerChannel!=null){
            this.localServerChannel.close();
        }
        channelGroup.close();
        contexts.clear();
    }

    private void finishContext(LocalChannel serverChannel) throws IOException {
        SshSessionContext context = contexts.get(serverChannel.remoteAddress());
        channelGroup.add(serverChannel);
        context.setServerChannel(clientSession,serverChannel);

    }

    public Promise<Void> start(){
        Promise<Void> promise = parent.eventLoop().newPromise();
        var cf = new ServerBootstrap()
                .group(parent.eventLoop())
                .channel(LocalServerChannel.class)
                .childHandler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    protected void initChannel(LocalChannel ch) throws Exception {
                        finishContext(ch);
                    }
                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        ctx.close();
                    }
                })
                .bind(localServer);
        Async.cascadeFailure0(cf,promise,channelFuture->{
            this.localServerChannel= (LocalServerChannel) channelFuture.channel();
            promise.trySuccess(null);
        });
        return promise;
    }

    public Future<Channel> allocTunnel(NetAddr dst){
        Promise<Channel>  promise = parent.eventLoop().newPromise();
        var key = new LocalAddress(UUID.randomUUID().toString());
        SshSessionContext context = new SshSessionContext(parent.eventLoop(),dst);
        contexts.put(key,context);
        var cf = new Bootstrap()
                .localAddress(key)
                .group(parent.eventLoop())
                .channel(LocalChannel.class)
                .handler(BaseChannelInitializer.EMPTY)
                .connect(localServer);

        cf.addListener(f->{
            if (!f.isSuccess()){
                promise.tryFailure(f.cause());
                contexts.remove(key);
            }else{
                context.setClientChannel((LocalChannel) cf.channel());
                Async.cascadeFailure(context.openFuture(),promise, _ ->promise.trySuccess(context.client));
            }
        });

        return promise;
    }

    public Future<Void> closeFuture(){
        return parent.closeFuture();
    }
}
