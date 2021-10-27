package io.crowds.proxy;

import io.crowds.Platform;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
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

    private Map<InetSocketAddress,DatagramChannel> tupleMap;

    public ChannelCreator(EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        this.tupleMap=new ConcurrentHashMap<>();
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
                .channel(Platform.getSocketChannelClass())
                .handler(initializer)
                .connect(address);
        return cf;
    }



    public DatagramChannel createDatagramChannel(InetSocketAddress tuple, DatagramOption option) {
        DatagramChannel channel = tupleMap.get(tuple);
        if (channel !=null) {
            logger.info("udp tuple {} fullcone {}",tuple,channel.localAddress());
            return channel;
        }

        synchronized(tuple.toString().intern()){
            boolean idleHandlerIsNull=option.getOnIdle()==null;
            if (idleHandlerIsNull){
                option.setOnIdle(ch->{
                    tupleMap.remove(tuple);
                    ch.close();
                });
            }
            channel = createDatagramChannel(option);
            if (!idleHandlerIsNull){
                channel.closeFuture().addListener(future -> tupleMap.remove(tuple));
            }
            tupleMap.put(tuple,channel);

            return channel;
        }

    }


    public DatagramChannel createDatagramChannel(DatagramOption option) {
        SocketAddress bindAddr = option.getBindAddr();
        if (bindAddr==null)
            bindAddr=new InetSocketAddress("0.0.0.0",0);

        DatagramChannel udpChannel= Platform.getDatagramChannel();
        if (option.isIpTransport()){
            udpChannel.config().setOption(EpollChannelOption.IP_TRANSPARENT,true);
        }
        eventLoopGroup.register(udpChannel);

        udpChannel.pipeline()
                .addLast(new IdleStateHandler(0,0,60))
                .addLast(new UdpChannelHandler(option.getOnIdle()));
        udpChannel.bind(bindAddr);
        return udpChannel;
    }


    private class UdpChannelHandler extends ChannelInboundHandlerAdapter{

        private Consumer<Channel> onIdle;

        public UdpChannelHandler(Consumer<Channel> onIdle) {
            this.onIdle = onIdle;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

            super.channelRead(ctx, msg instanceof DatagramPacket?((DatagramPacket) msg).content():msg);
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
