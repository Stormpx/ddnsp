package io.crowds.proxy.transport;

import io.crowds.proxy.*;
import io.crowds.proxy.common.BaseChannelInitializer;
import io.crowds.util.Inet;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;

public class DirectTransport implements Transport {

    protected ProtocolOption protocolOption;
    protected ChannelCreator channelCreator;

    public DirectTransport(ProtocolOption protocolOption, ChannelCreator channelCreator) {
        this.protocolOption = protocolOption;
        this.channelCreator = channelCreator;
    }

    private InetSocketAddress getLocalAddr(boolean ipv6){
        String dev = protocolOption.getTransport().getDev();
        if (dev==null)
            return null;

        InetAddress address = Inet.getAddress(dev, ipv6);
        if (address==null){
            throw new IllegalStateException("%s: no such device".formatted(dev));
        }
        return new InetSocketAddress(address,0);
    }

    private Future<Channel> createTcp(EventLoop eventLoop, NetAddr dst, BaseChannelInitializer initializer) throws SSLException {
        Promise<Channel> promise=eventLoop.newPromise();
        TlsOption tlsOption = protocolOption.getTls();
        if (tlsOption!=null&& tlsOption.isEnable()){
            initializer.tls(true,tlsOption.isAllowInsecure(),
                    tlsOption.getServerName()==null?dst.getHost():tlsOption.getServerName(),
                    dst.getPort());
        }
        var cf= channelCreator.createTcpChannel(eventLoop,getLocalAddr(dst.isIpv6()),dst.getAddress(), initializer);
        cf.addListener(f->{
            if (!f.isSuccess()){
                promise.tryFailure(f.cause());
                return;
            }
            promise.trySuccess(cf.channel());
        });
        return promise;
    }

    private Future<Channel> createUdp(EventLoop eventLoop,NetAddr dst,BaseChannelInitializer initializer) {
        Promise<Channel> promise = eventLoop.newPromise();
        channelCreator.createDatagramChannel(new DatagramOption().setBindAddr(getLocalAddr(dst.isIpv6())),initializer)
                .addListener(f->{
                    if (!f.isSuccess()){
                        promise.tryFailure(f.cause());
                    }else{
                        promise.trySuccess((Channel) f.get());
                    }
                });
        return promise;
    }

    @Override
    public Future<Channel> createChannel(EventLoop eventLoop, Destination destination) throws Exception {
        NetAddr addr = destination.addr();
        TP tp = destination.tp();
        BaseChannelInitializer initializer = new BaseChannelInitializer();
//        initializer.logLevel(LogLevel.INFO);
        if (protocolOption.getConnIdle()>0){
            initializer.connIdle(protocolOption.getConnIdle());
        }else if (tp==TP.UDP){
            initializer.connIdle(120);
        }

        return tp==TP.TCP?createTcp(eventLoop,addr,initializer):createUdp(eventLoop,addr,initializer);
    }
}
