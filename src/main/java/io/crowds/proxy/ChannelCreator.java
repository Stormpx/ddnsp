package io.crowds.proxy;

import io.crowds.Context;
import io.crowds.compoments.dns.VariantResolver;
import io.crowds.util.AddrType;
import io.crowds.util.Async;
import io.crowds.util.ChannelFactoryProvider;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stormpx.net.netty.PartialDatagramChannel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

public class ChannelCreator {
    private final static Logger logger= LoggerFactory.getLogger(ChannelCreator.class);

    private final VariantResolver variantResolver;
    private final EventLoopGroup eventLoopGroup;
    private final EventLoop executor;
    private final ChannelFactoryProvider channelFactoryProvider;
    private final Map<InetSocketAddress,Future<DatagramChannel>> foreignChannelLookups;

    public ChannelCreator(Context context) {
        this.eventLoopGroup = context.getEventLoopGroup();
        this.executor =context.getEventLoopGroup().next();
        this.channelFactoryProvider = context.getChannelFactoryProvider();
        this.variantResolver = context.getVariantResolver();
        this.foreignChannelLookups=new HashMap<>();
    }

    public ChannelCreator(EventLoopGroup eventLoopGroup, ChannelFactoryProvider channelFactoryProvider, VariantResolver variantResolver) {
        this.eventLoopGroup = eventLoopGroup;
        this.executor = eventLoopGroup.next();
        this.channelFactoryProvider = channelFactoryProvider;
        this.variantResolver = variantResolver;
        this.foreignChannelLookups=new HashMap<>();
    }

    public Bootstrap getBootstrap(EventLoop eventLoop){
        return new Bootstrap()
                .group(eventLoop==null?getEventLoopGroup().next():eventLoop)
                .resolver(variantResolver.getNettyResolver());
    }

    public EventLoopGroup getEventLoopGroup() {
        return eventLoopGroup;
    }


    public Future<Channel> createSocketChannel(EventLoop eventLoop, SocketAddress local, SocketAddress remote, ChannelInitializer<Channel> initializer) {
        Promise<Channel> promise = eventLoop.newPromise();
        var cf=getBootstrap(eventLoop)
                        .channelFactory(channelFactoryProvider.getSocketChannelFactory())
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                        .handler(initializer)
                        .connect(remote,local);
        cf.addListener(f->{
            if (cf.isCancelled()){
                promise.cancel(false);
            }if (!cf.isSuccess()){
                promise.tryFailure(cf.cause());
            }else{
                promise.trySuccess(cf.channel());
            }
        });
        return promise;
    }

    private Future<DatagramChannel> doCreateDatagramChannel(SocketAddress bindAddr,EventLoop eventLoop,boolean ipTransparent, ChannelInitializer<Channel> initializer){
        assert bindAddr!=null;
        assert eventLoop!=null;
        DatagramChannel udpChannel;
        if (bindAddr instanceof InetSocketAddress inetSocketAddress){
            udpChannel= channelFactoryProvider.getDatagramChannelFactory().newChannel(AddrType.of(inetSocketAddress.getAddress()).toNettyFamily());
        }else{
            udpChannel= channelFactoryProvider.getDatagramChannelFactory().newChannel();
        }
        if (!(udpChannel instanceof PartialDatagramChannel)) {
            udpChannel.config().setOption(ChannelOption.SO_REUSEADDR, true);
        }
        if (ipTransparent && udpChannel instanceof EpollDatagramChannel){
            udpChannel.config().setOption(EpollChannelOption.IP_TRANSPARENT,true);
        }
        if (initializer!=null) {
            udpChannel.pipeline().addLast(initializer);
        }
        eventLoop.register(udpChannel);

        Promise<DatagramChannel> promise=eventLoop.newPromise();
        udpChannel.bind(bindAddr)
                  .addListener(future -> {
                      if (!future.isSuccess()){
                          promise.tryFailure(future.cause());
                          return;
                      }
                      promise.trySuccess(udpChannel);
                  });
        return promise;
    }

    private Future<DatagramChannel> getOrCreateForeignDatagramChannel(InetSocketAddress bindAddr,EventLoop eventLoop, ChannelInitializer<Channel> initializer) {
        assert bindAddr!=null;
        assert eventLoop!=null;
        if (this.executor.inEventLoop()){
            Future<DatagramChannel> result = foreignChannelLookups.get(bindAddr);
            if (result!=null){
                return result;
            }
            var future = doCreateDatagramChannel(bindAddr,eventLoop,true,initializer);
            if (future.isDone()&&!future.isSuccess()){
                return future;
            }
            foreignChannelLookups.put(bindAddr,future);
            future.addListener((FutureListener<DatagramChannel>) f->{
                if (!f.isSuccess()){
                    foreignChannelLookups.remove(bindAddr,future);
                    return;
                }
                f.get().closeFuture().addListener(it->foreignChannelLookups.remove(bindAddr,future));
            });
            return future;
        }else{
            Promise<DatagramChannel> promise = this.executor.newPromise();
            this.executor.submit(()-> getOrCreateForeignDatagramChannel(bindAddr,eventLoop, initializer).addListener(Async.cascade(promise)));
            return promise;
        }
    }

    public Future<DatagramChannel> createDatagramChannel(DatagramOption option, ChannelInitializer<Channel> initializer) {
        SocketAddress bindAddr = option.getBindAddr();
        boolean ipTransparent = option.isIpTransparent();
        EventLoop eventLoop = option.getEventLoop()!=null? option.getEventLoop():eventLoopGroup.next();

        if (ipTransparent&&bindAddr==null){
            return eventLoop.newFailedFuture(new IllegalArgumentException("IP_TRANSPARENT must specify Bind Address"));
        }

        if (ipTransparent){
            return getOrCreateForeignDatagramChannel((InetSocketAddress) bindAddr,eventLoop, initializer);
        }

        SocketAddress localAddress = bindAddr != null ? bindAddr : new InetSocketAddress("0.0.0.0", 0);
        return doCreateDatagramChannel(localAddress,eventLoop, false,initializer);
    }



}
