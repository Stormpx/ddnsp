package io.crowds.proxy;

import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.direct.DirectProxyTransport;
import io.crowds.proxy.transport.direct.TcpEndPoint;
import io.crowds.proxy.transport.direct.UdpEndPoint;
import io.crowds.proxy.transport.shadowsocks.Cipher;
import io.crowds.proxy.transport.shadowsocks.ShadowsocksOption;
import io.crowds.proxy.transport.shadowsocks.ShadowsocksTransport;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;

public class Axis {
    private final static Logger logger= LoggerFactory.getLogger(Axis.class);
    private EventLoopGroup eventLoopGroup;
    private ChannelCreator channelCreator;
    private ProxyOption proxyOption;

    public Axis(EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        this.channelCreator = new ChannelCreator(eventLoopGroup);
    }

    public Axis setProxyOption(ProxyOption proxyOption) {
        this.proxyOption = proxyOption;
        return this;
    }

    private ProxyTransport getTransport(){
//                InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16823);
//                var option=new VmessOption()
//                        .setAddress(dest)
//                        .setSecurity(Security.ChaCha20_Poly1305)
//                        .setUser(new User(UUID.fromString("b831381d-6324-4d53-ad4f-8cda48b30811"),0));
//                option.setConnIdle(300);
//                return new VmessProxyTransport(option,eventLoopGroup,channelCreator);

//        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16827);
//        var option=new ShadowsocksOption()
//                .setAddress(dest)
//                .setCipher(Cipher.AES_256_GCM)
//                .setPassword("passpasspass");
//        option.setConnIdle(5);
//        option.setName("ss");
//        return  new ShadowsocksTransport(eventLoopGroup, channelCreator, option);

        return new DirectProxyTransport(eventLoopGroup,channelCreator);
    }

    private void bridging(EndPoint src,EndPoint dest,NetLocation netLocation){
        var proxyCtx=new ProxyContext(src,dest,netLocation);
        dest.bufferHandler(src::write);
        src.bufferHandler(dest::write);
    }

    private NetAddr getNetAddr(SocketAddress address){
        if (address instanceof InetSocketAddress){
            InetSocketAddress inetAddr= (InetSocketAddress) address;
            if (inetAddr.isUnresolved()){
                return new DomainNetAddr(inetAddr);
            }
        }
        return new NetAddr(address);
    }

    public void handleTcp(Channel channel,SocketAddress srcAddr,SocketAddress destAddr){
        try {
            channel.config().setAutoRead(false);
            ProxyTransport transport = getTransport();
            TcpEndPoint src = new TcpEndPoint(channel)
                    .exceptionHandler(e->logger.info("src {} caught exception :{}",channel.remoteAddress(),e.getMessage()));
            logger.info("channel {} to {}",srcAddr,destAddr);

            NetLocation netLocation = new NetLocation(getNetAddr(srcAddr),getNetAddr(destAddr), TP.TCP);
            transport.createEndPoint(netLocation)
                    .addListener(future -> {
                        if (!future.isSuccess()){
                            if (logger.isDebugEnabled())
                                logger.error("",future.cause());
                            logger.error("connect remote: {} failed cause: {}",netLocation.getDest().getAddress(),future.cause().getMessage());
                            channel.close();
                            return;
                        }
                        EndPoint dest= (EndPoint) future.get();
                        bridging(src,dest,netLocation);

                        channel.config().setAutoRead(true);
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleUdp(DatagramChannel datagramChannel,DatagramPacket packet){
        try {
            InetSocketAddress recipient = packet.recipient();
            InetSocketAddress sender = packet.sender();
            var src=new UdpEndPoint(datagramChannel,sender);
            ProxyTransport provider = getTransport();
            NetLocation netLocation = new NetLocation(getNetAddr(sender), getNetAddr(recipient), TP.UDP);
            provider.createEndPoint(netLocation)
                    .addListener(future -> {
                        if (!future.isSuccess()){
                            logger.info("......");
                            return;
                        }
                        EndPoint dest= (EndPoint) future.get();

                        bridging(src,dest,netLocation);

                        if (packet.content().isReadable())
                            dest.write(packet.content());
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public EventLoopGroup getEventLoopGroup() {
        return eventLoopGroup;
    }

    public ChannelCreator getChannelCreator() {
        return channelCreator;
    }
}
