package io.crowds.proxy;

import io.crowds.Platform;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollMode;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

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

    public Future<Channel> createTcpChannel(EventLoop eventLoop, SocketAddress local, SocketAddress remote, ChannelInitializer<Channel> initializer) {
        Bootstrap bootstrap = new Bootstrap();
        Promise<Channel> promise = eventLoop.newPromise();
        if (Epoll.isAvailable()){
            bootstrap.option(EpollChannelOption.EPOLL_MODE, EpollMode.LEVEL_TRIGGERED);
        }
        var cf=bootstrap.group(eventLoop)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .channel(Platform.getSocketChannelClass())
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




    public Future<DatagramChannel> createDatagramChannel(DatagramOption option, ChannelInitializer<Channel> initializer) {
        SocketAddress bindAddr = option.getBindAddr();

        SocketAddress localAddress = bindAddr != null ? bindAddr : new InetSocketAddress("0.0.0.0", 0);
        DatagramChannel udpChannel;
        if (localAddress instanceof InetSocketAddress inetSocketAddress){
            udpChannel= Platform.getDatagramChannel(inetSocketAddress.getAddress() instanceof Inet4Address? InternetProtocolFamily.IPv4:InternetProtocolFamily.IPv6);
        }else{
            udpChannel= Platform.getDatagramChannel();
        }
        udpChannel.config().setOption(ChannelOption.SO_REUSEADDR,true);
        if (option.isIpTransparent()&& udpChannel instanceof EpollDatagramChannel){
            udpChannel.config().setOption(EpollChannelOption.IP_TRANSPARENT,true);
        }
        if (initializer!=null) {
            udpChannel.pipeline().addLast(initializer);
        }

        EventLoop eventLoop = option.getEventLoop()!=null? option.getEventLoop():eventLoopGroup.next();
        eventLoop.register(udpChannel);

        Promise<DatagramChannel> promise=eventLoop.newPromise();
        udpChannel.bind(localAddress)
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
