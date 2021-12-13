package io.crowds.proxy;

import io.crowds.Platform;
import io.crowds.proxy.transport.UdpChannel;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.SucceededFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class ChannelCreator {
    private final static Logger logger= LoggerFactory.getLogger(ChannelCreator.class);
    private EventLoopGroup eventLoopGroup;

    private Map<String,Map<InetSocketAddress, Future<UdpChannel>>> spaceTupleMap;
    private Map<String,Map<InetSocketAddress,Object>> spaceTupleLockTable;

    public ChannelCreator(EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        this.spaceTupleMap=new ConcurrentHashMap<>();
        this.spaceTupleLockTable=new ConcurrentHashMap<>();
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

    public ChannelFuture createTcpChannel(EventLoop eventLoop,SocketAddress address, ChannelInitializer<Channel> initializer) {
        Bootstrap bootstrap = new Bootstrap();
        var cf=bootstrap.group(eventLoop)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .channel(Platform.getSocketChannelClass())
                .handler(initializer)
                .connect(address);
        return cf;
    }



    public Future<UdpChannel> createDatagramChannel(String group,InetSocketAddress tuple,DatagramOption option, ChannelInitializer<Channel> initializer) throws ExecutionException, InterruptedException {
        var tupleMap=spaceTupleMap.computeIfAbsent(group,k->new ConcurrentHashMap<>());
        Future<UdpChannel> udpFuture = tupleMap.get(tuple);
        if (udpFuture !=null) {
            if (udpFuture.isSuccess())
                logger.info("udp tuple {} fullcone {}",tuple,udpFuture.get().getDatagramChannel().localAddress());
            return udpFuture;
        }
        var lock=spaceTupleLockTable.computeIfAbsent(group,k->new ConcurrentHashMap<>()).computeIfAbsent(tuple,k->new Object());
        synchronized(lock){
            udpFuture=tupleMap.get(tuple);
            if (udpFuture!=null){
                if (udpFuture.isSuccess())
                    logger.info("udp tuple {} fullcone {}",tuple,udpFuture.get().getDatagramChannel().localAddress());
                return udpFuture;
            }

            udpFuture=createUdpChannel(option, initializer);
            tupleMap.put(tuple,udpFuture);
            Future<UdpChannel> finalUdpFuture = udpFuture;
            udpFuture.addListener(future -> {
                        if (!future.isSuccess()){
                            var f=tupleMap.get(tuple);
                            if (finalUdpFuture==f)
                                tupleMap.remove(tuple);
                        }else{
                            DatagramChannel channel = finalUdpFuture.get().getDatagramChannel();
                            channel.closeFuture().addListener(it->{
                                if (tupleMap.get(tuple)==finalUdpFuture)
                                    tupleMap.remove(tuple);
                            });
                        }
                    });

            return udpFuture;

        }

    }

    private Future<UdpChannel> createUdpChannel(DatagramOption option, ChannelInitializer<Channel> initializer){
        Promise<UdpChannel> promise = eventLoopGroup.next().newPromise();
        createDatagramChannel(option,initializer)
                .addListener(future -> {
                    if (!future.isSuccess()){
                        promise.tryFailure(future.cause());
                        return;
                    }
                    DatagramChannel datagramChannel= (DatagramChannel) future.get();
                    UdpChannel channel=new UdpChannel(datagramChannel);
                    promise.trySuccess(channel);
                });

        return promise;
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
