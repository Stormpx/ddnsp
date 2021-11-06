package io.crowds.proxy.transport.shadowsocks;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.TP;
import io.crowds.proxy.transport.EndPoint;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
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
    public ShadowsocksEndpoint(EndPoint base, ShadowsocksOption option, NetLocation netLocation) {
        this.base = base;
        this.option = option;
        this.netLocation = netLocation;
        init();
    }

    private void init(){
        encodeAddress();
        base.bufferHandler(buf -> {
            fireBuf(buf);
        });

    }

    private void encodeAddress(){
        this.addressBuffer =Unpooled.buffer(4);
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
    }


    @Override
    public void write(ByteBuf buf) {
        if (netLocation.getTp()== TP.TCP){
            if (this.addressBuffer !=null){
                buf= Unpooled.compositeBuffer().addComponent(true,this.addressBuffer).addComponent(true,buf);
                this.addressBuffer =null;
            }
            base.write(buf);
        }else{
            assert this.addressBuffer!=null;
            base.write(Unpooled.compositeBuffer().addComponent(true,this.addressBuffer.retain()).addComponent(true,buf));
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
