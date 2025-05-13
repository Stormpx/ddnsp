package io.crowds.proxy;

import io.crowds.Context;
import io.crowds.proxy.dns.FakeContext;
import io.crowds.proxy.dns.FakeDns;
import io.crowds.proxy.dns.FakeOption;
import io.crowds.proxy.routing.CachedRouter;
import io.crowds.proxy.routing.Router;
import io.crowds.proxy.select.Transport;
import io.crowds.proxy.select.TransportProvider;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.proxy.transport.TcpEndPoint;
import io.crowds.proxy.transport.UdpEndPoint;
import io.crowds.util.Exceptions;
import io.crowds.util.IPCIDR;
import io.crowds.util.Lambdas;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Axis {
    private final static Logger logger= LoggerFactory.getLogger(Axis.class);
    private final Context context;
    private ChannelCreator channelCreator;
    private ProxyOption proxyOption;

    private FakeDns fakeDns;

    private Router router;

    private TransportProvider transportProvider;

    private UdpMappings mappings;

    public Axis(Context context) {
        this.context = context;
        this.channelCreator = new ChannelCreator(context);
        this.mappings=new UdpMappings();
    }


    public Axis setProxyOption(ProxyOption proxyOption) {

        if (proxyOption.getRules()!=null){
            if (this.proxyOption==null||!proxyOption.getRules().equals(this.proxyOption.getRules())) {
                this.router = new CachedRouter(proxyOption.getRules(),2,6,12);
                if (this.fakeDns != null) this.fakeDns.setRouter(router);
                logger.info("router rules setup.");
            }
        }
        if (this.transportProvider==null){
            this.transportProvider =new TransportProvider(this,proxyOption.getProxies(),proxyOption.getSelectors());
        }
        if (this.router!=null&&this.fakeDns==null&&proxyOption.getFakeDns()!=null){
            createFakeDns(proxyOption.getFakeDns());
        }

        this.proxyOption = proxyOption;

        return this;
    }

    private void createFakeDns(FakeOption fakeOption){
        try {
            IPCIDR ipv4Cidr=null;
            IPCIDR ipv6Cidr=null;
            if (fakeOption.getIpv4Pool()!=null&&!fakeOption.getIpv4Pool().isBlank()){
                ipv4Cidr=new IPCIDR(fakeOption.getIpv4Pool());
                if (!(ipv4Cidr.getAddress() instanceof Inet4Address)){
                    throw new IllegalArgumentException("expect ipv4 cidr");
                }
            }
            if (fakeOption.getIpv6Pool()!=null&&!fakeOption.getIpv6Pool().isBlank()){
                ipv6Cidr=new IPCIDR(fakeOption.getIpv6Pool());
                if (!(ipv6Cidr.getAddress() instanceof Inet6Address)){
                    throw new IllegalArgumentException("expect ipv6 cidr");
                }
            }
            this.fakeDns=new FakeDns(context.getEventLoopGroup().next(), router,ipv4Cidr,ipv6Cidr,fakeOption.getDestStrategy());
            logger.info("fake dns setup.");
        } catch (Exception e) {
            logger.warn("failed to create fakeDns cause:{}",e.getMessage());
        }

    }




    private Transport getTransport(ProxyContext proxyContext){
        if (this.router==null){
            return transportProvider.direct();
        }
//        if (proxyContext.getFakeContext()!=null){
//            return transportProvider.getTransport(proxyContext);
//        }

        NetLocation netLocation = proxyContext.getNetLocation();
        String tag = this.router.routing(netLocation);
        proxyContext.withTag(tag);

        return transportProvider.getTransport(proxyContext);

    }


    private NetAddr getNetAddr(SocketAddress address){
        if (address instanceof InetSocketAddress inetAddr){
            return NetAddr.of(inetAddr);
        }
        return new NetAddr(address);
    }

    private FakeContext getFakeContext(NetAddr netAddr){
        if (this.fakeDns==null)
            return null;
        if (netAddr instanceof DomainNetAddr){
            return null;
        }
        InetAddress address = netAddr.getAsInetAddr().getAddress();
        if (!this.fakeDns.isFakeIp(address)){
            return null;
        }
        FakeContext fakeContext = this.fakeDns.getFake(address);
        return fakeContext;
    }


    private ProxyContext createContext(EventLoop eventLoop,NetLocation netLocation){
        FakeContext fakeContext=getFakeContext(netLocation.getDst());
        if (fakeContext!=null)
            netLocation=new NetLocation(netLocation.getSrc(), fakeContext.getNetAddr(netLocation.getDst().getPort()), netLocation.getTp());

        ProxyContext proxyContext = new ProxyContext(eventLoop,netLocation);
        proxyContext.withFakeContext(fakeContext);

        return proxyContext;
    }

    private void logException(SocketAddress srcAddr,SocketAddress dstAddr,Throwable t,boolean src){
        if (Exceptions.isExpected(t)){
            if (Exceptions.shouldLogMessage(t)) {
                logger.error("{}->{} caught exception from {}: {}",srcAddr,dstAddr,src?"src":"dst",t.getMessage());
            }
        }else{
            logger.error("{}->{} caught exception from {}",srcAddr,dstAddr,src?"src":"dst",t);
        }
    }

    public Promise<Void> handleTcp(Channel channel,SocketAddress srcAddr,SocketAddress dstAddr){
        Promise<Void> promise = channel.eventLoop().newPromise();
        try {

            TcpEndPoint src = new TcpEndPoint(channel);
            src.exceptionHandler(t->logException(srcAddr,dstAddr,t,true));


            NetLocation netLocation = new NetLocation(getNetAddr(srcAddr),getNetAddr(dstAddr), TP.TCP);

            ProxyContext proxyContext = createContext(channel.eventLoop(),netLocation);

            Transport transport=getTransport(proxyContext);
            logger.info("tcp {} to {} via [{}]",proxyContext.getNetLocation().getSrc(),proxyContext.getNetLocation().getDst(), transport.getChain());
            ProxyTransport proxy = transport.proxy();
            proxy.createEndPoint(proxyContext)
                    .addListener(future -> {
                        if (!future.isSuccess()){
                            if (logger.isDebugEnabled())
                                logger.error("",future.cause());
                            logger.error("failed to connect remote: {} > {}",proxyContext.getNetLocation().getDst().getAddress(),future.cause().getMessage());
                            promise.tryFailure(future.cause());
                            src.close();
                            return;
                        }
                        EndPoint dst= (EndPoint) future.get();
                        dst.exceptionHandler(t->logException(netLocation.getSrc().getAddress(),netLocation.getDst().getAddress(),t,false));
                        proxyContext.bridging(src,dst);
                        promise.trySuccess(null);
                        proxyContext.setAutoRead();
                    });
            return promise;
        } catch (Exception e) {
            logger.error("",e);
            promise.tryFailure(e);
            channel.close();
        }
        return promise;
    }

    public void handleUdp0(DatagramChannel datagramChannel, DatagramPacket packet, Consumer<DatagramPacket> fallbackPacketHandler){
        try {
            NetAddr recipient = getNetAddr(packet.recipient());
            NetAddr sender = getNetAddr(packet.sender());
            FakeContext fakeContext=getFakeContext(recipient);
            if (fakeContext!=null){
                recipient = fakeContext.getNetAddr(recipient.getPort());
            }
            NetLocation netLocation = new NetLocation(sender, recipient, TP.UDP);

            mappings.getOrCreate(netLocation, Lambdas.rethrowSupplier(()->{
                ProxyContext proxyContext = new ProxyContext(datagramChannel.eventLoop(), netLocation)
                        .fallbackPacketHandler(fallbackPacketHandler)
                        .withFakeContext(fakeContext);
                Promise<ProxyContext> promise = proxyContext.getEventLoop().newPromise();
                var src=new UdpEndPoint(datagramChannel,netLocation.getSrc());
                src.exceptionHandler(t->logException(netLocation.getSrc().getAddress(),netLocation.getDst().getAddress(),t,true));
                Transport transport=getTransport(proxyContext);
                logger.info("udp {} to {} via [{}]",proxyContext.getNetLocation().getSrc(),proxyContext.getNetLocation().getDst(),transport.getChain());
                ProxyTransport proxy = transport.proxy();
                proxy.createEndPoint(proxyContext)
                        .addListener(future -> {
                            if (!future.isSuccess()){
                                promise.tryFailure(future.cause());
                                return;
                            }
                            EndPoint dst= (EndPoint) future.get();
                            dst.exceptionHandler(t->logException(netLocation.getSrc().getAddress(),netLocation.getDst().getAddress(),t,false));
                            proxyContext.bridging(src,dst);
                            promise.trySuccess(proxyContext);
                            proxyContext.setAutoRead();

                        });
                return promise;
            })).addListener(f->{
                if (!f.isSuccess()){
                    if (logger.isDebugEnabled())
                        logger.error("",f.cause());
                    ReferenceCountUtil.safeRelease(packet);
                    return;
                }

                ProxyContext proxyContext= (ProxyContext) f.get();
                proxyContext.getDst().write(packet);

            })
            ;

        } catch (Exception e) {
            ReferenceCountUtil.safeRelease(packet);
            logger.error("",e);
        }
    }

    public Context getContext() {
        return context;
    }

    public ChannelCreator getChannelCreator() {
        return channelCreator;
    }

    public FakeDns getFakeDns() {
        return fakeDns;
    }


    public static class UdpMappings{
        private final Map<NetLocation, Future<ProxyContext>> contexts;


        public UdpMappings() {
            this.contexts =new ConcurrentHashMap<>();
        }


        private Future<ProxyContext> get(NetLocation netLocation){
            return contexts.get(netLocation);
        }


        public Future<ProxyContext> getOrCreate(NetLocation netLocation, Supplier<Future<ProxyContext>> supplier){
            Future<ProxyContext> future = get(netLocation);
            if (future!=null)
                return future;
            return contexts.computeIfAbsent(netLocation,k-> {
                Future<ProxyContext> proxyContextFuture = supplier.get();
                return proxyContextFuture
                                .addListener(p->{
                                    if (!p.isSuccess()){
                                        GlobalEventExecutor.INSTANCE.execute(()->contexts.remove(netLocation,proxyContextFuture));
                                        return;
                                    }
                                    ProxyContext context= (ProxyContext) p.get();
                                    context.closeHandler(v->contexts.remove(netLocation,proxyContextFuture));
                                });
                    });

        }


    }
}
