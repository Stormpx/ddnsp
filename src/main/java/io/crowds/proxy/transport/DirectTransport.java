package io.crowds.proxy.transport;

import io.crowds.Ddnsp;
import io.crowds.proxy.*;
import io.crowds.proxy.common.BaseChannelInitializer;
import io.crowds.util.Async;
import io.crowds.util.Inet;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;
import io.netty.util.concurrent.SucceededFuture;

import javax.net.ssl.SSLException;
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
        TransportOption transportOption = protocolOption.getTransport();
        if (transportOption==null)
            return null;
        String dev = transportOption.getDev();
        if (dev==null)
            return null;

        InetAddress address = Inet.getDeviceAddress(dev, ipv6);
        if (address==null){
            throw new IllegalStateException("%s: no such device".formatted(dev));
        }
        return new InetSocketAddress(address,0);
    }

    private Future<NetAddr> resolve(EventLoop eventLoop,NetAddr addr){
        if (addr instanceof DomainNetAddr domain){
            Promise<NetAddr> promise = eventLoop.newPromise();
            Ddnsp.dnsResolver().resolve(domain.getHost(),null)
                    .map(inetAddr->new NetAddr(new InetSocketAddress(inetAddr, addr.getPort())))
                    .onComplete(Async.futureCascadeCallback(promise));
            return promise;
        }
        return new SucceededFuture<>(eventLoop,addr);
    }

    private Future<Channel> createTcp(EventLoop eventLoop, NetAddr dst, BaseChannelInitializer initializer) throws SSLException {
        Promise<Channel> promise=eventLoop.newPromise();
        TlsOption tlsOption = protocolOption.getTls();
        if (tlsOption!=null&& tlsOption.isEnable()){
            initializer.tls(true,tlsOption.isAllowInsecure(),
                    tlsOption.getServerName()==null?dst.getHost():tlsOption.getServerName(),
                    dst.getPort());
        }
        Async.cascadeFailure(resolve(eventLoop,dst),promise,resolveFuture->{
            NetAddr dest = resolveFuture.get();
            var cf= channelCreator.createTcpChannel(eventLoop,getLocalAddr(dest.isIpv6()),dest.getAddress(), initializer);
            PromiseNotifier.cascade(cf,promise);
        });
        return promise;
    }

    private Future<Channel> createUdp(EventLoop eventLoop,NetAddr dst,BaseChannelInitializer initializer) {
        Promise<Channel> promise = eventLoop.newPromise();
        resolve(eventLoop,dst)
                .addListener(resolveFuture->{
                    if (!resolveFuture.isSuccess()){
                        promise.tryFailure(new SocketException("bind with domain failed",resolveFuture.cause()));
                        return;
                    }
                    NetAddr dest = (NetAddr) resolveFuture.get();
                    var future = channelCreator.createDatagramChannel(new DatagramOption().setBindAddr(getLocalAddr(dest.isIpv6())),initializer);
                    PromiseNotifier.cascade(future,promise);
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
