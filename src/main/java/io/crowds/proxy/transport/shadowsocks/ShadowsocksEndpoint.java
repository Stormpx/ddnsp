package io.crowds.proxy.transport.shadowsocks;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.TP;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.UdpChannel;
import io.crowds.proxy.transport.direct.TcpEndPoint;
import io.crowds.proxy.transport.direct.UdpEndPoint;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class ShadowsocksEndpoint extends EndPoint {
    private final static Logger logger= LoggerFactory.getLogger(ShadowsocksEndpoint.class);
    private EndPoint base;
    private ShadowsocksOption option;
    private NetLocation netLocation;

    private ByteBuf addressBuffer;


    public ShadowsocksEndpoint(Channel channel, ShadowsocksOption option, NetLocation netLocation) {
        this.base = new TcpEndPoint(channel);
        this.option = option;
        this.netLocation = netLocation;
        init();
    }

    public ShadowsocksEndpoint(UdpChannel udpChannel, ShadowsocksOption option, NetLocation netLocation,NetAddr serverAddr) {

        udpChannel.packetHandler(netLocation.getDest(), this::fireBuf);

        this.base = new UdpEndPoint(udpChannel.getDatagramChannel(),serverAddr);
        this.option = option;
        this.netLocation = netLocation;
        init();
    }


    private void init(){
        base.writabilityHandler(super::fireWriteable);
        base.bufferHandler(this::fireBuf);
        encodeAddress();
    }

    private void encodeAddress(){
        this.addressBuffer =Unpooled.buffer(7);
        NetAddr dest = netLocation.getDest();
        if (dest.isIpv4()){
            addressBuffer.writeByte(0x01);
        }else if (dest.isIpv6()){
            addressBuffer.writeByte(0x04);
        }else{
            addressBuffer.writeByte(0x03);
            String host = dest.getHost();
            if (host.length()>256){
                throw new IllegalArgumentException("dest domain "+ host +" to long");
            }
            addressBuffer.writeByte(host.length());
        }
        addressBuffer.writeBytes(dest.getByte()).writeShort(dest.getPort());
        if (netLocation.getTp()==TP.TCP){
            base.write(this.addressBuffer);
        }
    }


    @Override
    public void write(Object msg) {
        if (msg instanceof DatagramPacket packet){
            msg=packet.content();
        }
        if (netLocation.getTp()== TP.TCP){
            base.write(msg);
        }else{
            assert this.addressBuffer!=null;
            base.write(Unpooled.compositeBuffer().addComponent(true,this.addressBuffer.retain()).addComponent(true, (ByteBuf) msg));
        }
    }

    @Override
    public Channel channel() {
        return base.channel();
    }

    @Override
    public void close() {
        base.close();
    }

    @Override
    public Future<Void> closeFuture() {
        return base.closeFuture();
    }
}
