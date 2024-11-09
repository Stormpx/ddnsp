package io.crowds.proxy.transport;

import io.crowds.Ddnsp;
import io.crowds.proxy.*;
import io.crowds.proxy.common.BaseChannelInitializer;
import io.crowds.proxy.common.DynamicRecipientLookupHandler;
import io.crowds.util.AddrType;
import io.crowds.util.Async;
import io.crowds.util.Inet;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
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
    private InetAddress localAddr4;
    private InetAddress localAddr6;

    public DirectTransport(ProtocolOption protocolOption, ChannelCreator channelCreator) {
        this.protocolOption = protocolOption;
        this.channelCreator = channelCreator;
    }

    private InetSocketAddress getLocalAddr(boolean ipv6) throws SocketException {
        TransportOption transportOption = protocolOption.getTransport();
        if (transportOption==null)
            return null;
        String dev = transportOption.getDev();
        if (dev==null)
            return null;
        InetAddress devAddr;
        if (!ipv6) {
            devAddr = localAddr4;
            if (devAddr==null){
                devAddr = Inet.getDeviceAddress(dev, false);
                this.localAddr4 = devAddr;
            }
        } else {
            devAddr = localAddr6;
            if (devAddr==null){
                devAddr = Inet.getDeviceAddress(dev, true);
                this.localAddr6 = devAddr;
            }
        }
        if (devAddr==null){
            throw new SocketException("network interface %s does not have %s address".formatted(dev,ipv6?"v6":"v4"));
        }
        return new InetSocketAddress(devAddr,0);
    }

    private Future<NetAddr> resolve(EventLoop eventLoop,AddrType preferType,NetAddr addr){
        if (addr instanceof DomainNetAddr domain){
            Promise<NetAddr> promise = eventLoop.newPromise();
            Ddnsp.dnsResolver().resolve(domain.getHost(),preferType)
//                 .recover(e->Ddnsp.dnsResolver().resolve(domain.getHost(),preferType==AddrType.IPV4?AddrType.IPV6:AddrType.IPV4))
                 .map(inetAddr->new NetAddr(new InetSocketAddress(inetAddr, addr.getPort())))
                 .onComplete(Async.futureCascadeCallback(promise));
            return promise;
        }
        return new SucceededFuture<>(eventLoop,addr);
    }

    private Future<Channel> createTcp(EventLoop eventLoop,AddrType preferType, NetAddr dst, BaseChannelInitializer initializer) throws SSLException {
        Promise<Channel> promise=eventLoop.newPromise();
        TlsOption tlsOption = protocolOption.getTls();
        if (tlsOption!=null&& tlsOption.isEnable()){
            initializer.tls(true,tlsOption.isAllowInsecure(),
                    tlsOption.getServerName()==null?dst.getHost():tlsOption.getServerName(),
                    dst.getPort());
        }
        Async.cascadeFailure(resolve(eventLoop,preferType,dst),promise,resolveFuture->{
            NetAddr dest = resolveFuture.get();
            var cf= channelCreator.createTcpChannel(eventLoop,getLocalAddr(dest.isIpv6()),dest.getAddress(), initializer);
            PromiseNotifier.cascade(cf,promise);
        });
        return promise;
    }

    private Future<Channel> createUdp(EventLoop eventLoop,AddrType preferType,NetAddr dst,BaseChannelInitializer initializer) {
        Promise<Channel> promise = eventLoop.newPromise();
        resolve(eventLoop,preferType,dst)
                .addListener(resolveFuture->{
                    if (!resolveFuture.isSuccess()){
                        promise.tryFailure(new SocketException("unable resolve "+dst,resolveFuture.cause()));
                        return;
                    }
                    NetAddr dest = (NetAddr) resolveFuture.get();
                    initializer.initializer(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline()
                              .addLast(new DynamicRecipientLookupHandler(Ddnsp.dnsResolver()));
                        }
                    });
                    var future = channelCreator.createDatagramChannel(new DatagramOption().setBindAddr(getLocalAddr(dest.isIpv6())),initializer);
                    PromiseNotifier.cascade(future,promise);
                });
        return promise;
    }

    public Future<Channel> createChannelInternal(EventLoop eventLoop, Destination destination, AddrType preferType) throws Exception {
        NetAddr addr = destination.addr();
        TP tp = destination.tp();
        BaseChannelInitializer initializer = new BaseChannelInitializer();
//        initializer.logLevel(LogLevel.INFO);
        if (protocolOption.getConnIdle()>0){
            initializer.connIdle(protocolOption.getConnIdle());
        }else if (tp==TP.UDP){
            initializer.connIdle(120);
        }

        return tp==TP.TCP?createTcp(eventLoop,preferType,addr,initializer):createUdp(eventLoop,preferType,addr,initializer);
    }

    @Override
    public Future<Channel> openChannel(EventLoop eventLoop, Destination destination, AddrType preferType, Transport delegate) throws Exception {
        if (delegate!=null&&delegate!=this){
            return delegate.openChannel(eventLoop,destination,preferType,delegate);
        }else{
            return createChannelInternal(eventLoop, destination, preferType);
        }
    }
}
