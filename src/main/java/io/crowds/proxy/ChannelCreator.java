package io.crowds.proxy;

import io.crowds.Platform;
import io.crowds.util.Inet;
import io.crowds.util.Strs;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChannelCreator {
    private final static Logger logger= LoggerFactory.getLogger(ChannelCreator.class);
    private EventLoopGroup eventLoopGroup;


    public ChannelCreator(EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
    }

    public EventLoopGroup getEventLoopGroup() {
        return eventLoopGroup;
    }


    public ChannelFuture createTcpChannel(SocketAddress address, ChannelInitializer<Channel> initializer) {
        Bootstrap bootstrap = new Bootstrap();
        var cf=bootstrap.group(eventLoopGroup)
                .channel(Platform.getSocketChannelClass())
                .handler(initializer)
                .connect(address);
        return cf;
    }

    public ChannelFuture createTcpChannel(EventLoop eventLoop,SocketAddress local,SocketAddress remote, ChannelInitializer<Channel> initializer) {
        Bootstrap bootstrap = new Bootstrap();
        var cf=bootstrap.group(eventLoop)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .channel(Platform.getSocketChannelClass())
                .handler(initializer)
                .connect(remote,local);
        return cf;
    }




    public Future<DatagramChannel> createDatagramChannel(DatagramOption option, ChannelInitializer<Channel> initializer) {
        SocketAddress bindAddr = option.getBindAddr();


        DatagramChannel udpChannel= Platform.getDatagramChannel();
        udpChannel.config().setOption(ChannelOption.SO_REUSEADDR,true);
        if (option.isIpTransport()&& udpChannel instanceof EpollDatagramChannel){
            udpChannel.config().setOption(EpollChannelOption.IP_TRANSPARENT,true);
        }
        if (initializer!=null) {
            udpChannel.pipeline().addLast(initializer);
        }
        EventLoop eventLoop = eventLoopGroup.next();
        eventLoop.register(udpChannel);

        Promise<DatagramChannel> promise=eventLoop.newPromise();
        udpChannel.bind(bindAddr!=null?bindAddr:new InetSocketAddress(0))
            .addListener(future -> {
                if (!future.isSuccess()){
                    promise.tryFailure(future.cause());
                    return;
                }
                promise.trySuccess(udpChannel);
            });
        return promise;
    }



}
