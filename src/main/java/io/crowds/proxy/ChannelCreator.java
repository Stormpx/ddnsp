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
import java.util.function.Consumer;

public class ChannelCreator {
    private final static Logger logger= LoggerFactory.getLogger(ChannelCreator.class);
    private ProxyOption proxyOption;
    private EventLoopGroup eventLoopGroup;

    private Map<String,Map<InetSocketAddress, UdpChannel>> spaceTupleMap;

    public ChannelCreator(EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        this.spaceTupleMap=new ConcurrentHashMap<>();
    }

    public EventLoopGroup getEventLoopGroup() {
        return eventLoopGroup;
    }

    public ChannelCreator setProxyOption(ProxyOption proxyOption) {
        this.proxyOption = proxyOption;
        return this;
    }

    public ChannelFuture createTcpChannel(SocketAddress address, ChannelInitializer<Channel> initializer) {
        Bootstrap bootstrap = new Bootstrap();
        var cf=bootstrap.group(eventLoopGroup)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,0)
                .channel(Platform.getSocketChannelClass())
                .handler(initializer)
                .connect(address);
        return cf;
    }



    public Future<UdpChannel> createDatagramChannel(String group,InetSocketAddress tuple,DatagramOption option, ChannelInitializer<Channel> initializer) {
        var tupleMap=spaceTupleMap.computeIfAbsent(group,k->new ConcurrentHashMap<>());
        UdpChannel ch = tupleMap.get(tuple);
        if (ch !=null) {
            logger.info("udp tuple {} fullcone {}",tuple,ch.getDatagramChannel().localAddress());
            return new SucceededFuture<>(ch.getDatagramChannel().eventLoop(),ch);
        }

        synchronized(tuple.toString().intern()){
            ch=tupleMap.get(tuple);
            if (ch!=null){
                logger.info("udp tuple {} fullcone {}",tuple,ch.getDatagramChannel().localAddress());
                return new SucceededFuture<>(ch.getDatagramChannel().eventLoop(),ch);
            }
            Promise<UdpChannel> promise = eventLoopGroup.next().newPromise();
            createDatagramChannel( option,initializer).addListener(future -> {
                if (!future.isSuccess()){
                    promise.tryFailure(future.cause());
                    return;
                }
                DatagramChannel datagramChannel= (DatagramChannel) future.get();
                datagramChannel.closeFuture().addListener(it -> tupleMap.remove(tuple));
                UdpChannel channel=new UdpChannel(datagramChannel);
                tupleMap.put(tuple,channel);
                promise.trySuccess(channel);
            });

            return promise;
        }

    }


    public Future<DatagramChannel> createDatagramChannel(DatagramOption option, ChannelInitializer<Channel> initializer) {
        SocketAddress bindAddr = option.getBindAddr();
        if (bindAddr==null)
            bindAddr=new InetSocketAddress("0.0.0.0",0);

        DatagramChannel udpChannel= Platform.getDatagramChannel();
        if (option.isIpTransport()&& udpChannel instanceof EpollDatagramChannel){
            udpChannel.config().setOption(EpollChannelOption.IP_TRANSPARENT,true);
        }
        if (initializer!=null) {
            udpChannel.pipeline().addLast(initializer);
        }
        eventLoopGroup.register(udpChannel);


        Promise<DatagramChannel> promise=eventLoopGroup.next().newPromise();
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


    private class UdpChannelHandler extends ChannelInboundHandlerAdapter{

        private Consumer<Channel> onIdle;

        public UdpChannelHandler(Consumer<Channel> onIdle) {
            this.onIdle = onIdle;
        }



        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent){
                if (onIdle!=null)
                    onIdle.accept(ctx.channel());
            }else
                super.userEventTriggered(ctx, evt);
        }
    }
}
