package io.crowds.proxy.transport;

import io.crowds.Ddnsp;
import io.crowds.proxy.*;
import io.crowds.proxy.common.BaseChannelInitializer;
import io.crowds.proxy.common.DynamicRecipientLookupHandler;
import io.crowds.util.AddrType;
import io.crowds.util.Async;
import io.crowds.util.Inet;
import io.crowds.util.Strs;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;
import io.netty.util.concurrent.SucceededFuture;

import javax.net.ssl.SSLException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Objects;

public class DirectTransport implements Transport {

    protected ProtocolOption protocolOption;
    protected ChannelCreator channelCreator;
    private final String dev;
    private volatile InetSocketAddress localAddressV4;
    private volatile InetSocketAddress localAddressV6;

    public DirectTransport(ProtocolOption protocolOption, ChannelCreator channelCreator) {
        Objects.requireNonNull(protocolOption);
        Objects.requireNonNull(channelCreator);
        this.protocolOption = protocolOption;
        this.channelCreator = channelCreator;
        TransportOption transport = protocolOption.getTransport();
        this.dev = transport != null ? transport.getDev() : null;
    }

    private InetSocketAddress getLocalAddressV4(){
        InetSocketAddress localAddress = this.localAddressV4;
        if (localAddress==null){
            synchronized(this){
                localAddress = this.localAddressV4;
                if (localAddress==null) {
                    var deviceAddress = Inet.getDeviceAddressByIdentity(dev, false);
                    if (deviceAddress == null) {
                        //fallback address, means null
                        deviceAddress = Inet.ANY_ADDRESS_V4;
                    }
                    localAddress = new InetSocketAddress(deviceAddress,0);
                    this.localAddressV4 = localAddress;
                }
            }
        }
        return localAddress;
    }

    private InetSocketAddress getLocalAddressV6(){
        InetSocketAddress localAddress = this.localAddressV6;
        if (localAddress==null){
            synchronized(this){
                localAddress = this.localAddressV6;
                if (localAddress==null) {
                    var deviceAddress = Inet.getDeviceAddressByIdentity(dev, true);
                    if (deviceAddress == null) {
                        //fallback address, means null
                        deviceAddress = Inet.ANY_ADDRESS_V6;
                    }
                    localAddress = new InetSocketAddress(deviceAddress,0);
                    this.localAddressV6 = localAddress;
                }
            }
        }
        return localAddress;
    }

    private InetSocketAddress getLocalAddress(boolean ipv6) throws SocketException {
        if (dev==null){
            return null;
        }
        InetSocketAddress localAddress;
        if (ipv6){
            localAddress = getLocalAddressV6();
        }else{
            localAddress = getLocalAddressV4();
        }
        if (localAddress.getAddress().isAnyLocalAddress()){
            throw new SocketException("Network interface %s does not have %s address".formatted(dev,ipv6?"v6":"v4"));
        }
        return localAddress;
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
            initializer.bindToDevice(dev);
            var cf= channelCreator.createSocketChannel(eventLoop, getLocalAddress(dest.isIpv6()),dest.getAddress(), initializer);
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
                    initializer.bindToDevice(dev);
                    DatagramOption datagramOption = new DatagramOption()
                            .setEventLoop(eventLoop)
                            .setBindAddr(getLocalAddress(dest.isIpv6()));
                    var future = channelCreator.createDatagramChannel(datagramOption,initializer);
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
