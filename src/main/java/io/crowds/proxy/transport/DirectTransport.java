package io.crowds.proxy.transport;

import io.crowds.proxy.*;
import io.crowds.proxy.common.BaseChannelInitializer;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.handler.logging.LogLevel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import javax.net.ssl.SSLException;

public class DirectTransport implements Transport {

    protected ProtocolOption protocolOption;
    protected ChannelCreator channelCreator;

    public DirectTransport(ProtocolOption protocolOption, ChannelCreator channelCreator) {
        this.protocolOption = protocolOption;
        this.channelCreator = channelCreator;
    }

    private Future<Channel> createTcp(EventLoop eventLoop, NetAddr dest, BaseChannelInitializer initializer) throws SSLException {
        Promise<Channel> promise=eventLoop.newPromise();
        TlsOption tlsOption = protocolOption.getTls();
        if (tlsOption!=null&& tlsOption.isEnable()){
            initializer.tls(true,tlsOption.isAllowInsecure(),
                    tlsOption.getServerName()==null?dest.getHost():tlsOption.getServerName(),
                    dest.getPort());
        }
        var cf= channelCreator.createTcpChannel(eventLoop,dest.getAddress(), initializer);
        cf.addListener(f->{
            if (!f.isSuccess()){
                promise.tryFailure(f.cause());
                return;
            }
            promise.trySuccess(cf.channel());
        });
        return promise;
    }

    private Future<Channel> createUdp(EventLoop eventLoop,BaseChannelInitializer initializer) {
        Promise<Channel> promise = eventLoop.newPromise();
        channelCreator.createDatagramChannel(new DatagramOption(),initializer)
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
        initializer.logLevel(LogLevel.INFO);
        if (protocolOption.getConnIdle()>0){
            initializer.connIdle(protocolOption.getConnIdle());
        }else if (tp==TP.UDP){
            initializer.connIdle(120);
        }

        return tp==TP.TCP?createTcp(eventLoop,addr,initializer):createUdp(eventLoop,initializer);
    }
}
