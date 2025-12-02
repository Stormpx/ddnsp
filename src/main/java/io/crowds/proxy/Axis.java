package io.crowds.proxy;

import io.crowds.Context;
import io.crowds.proxy.common.NatMap;
import io.crowds.proxy.common.sniff.HostnameSniffer;
import io.crowds.proxy.common.sniff.SniffOption;
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
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Gatherers;
import java.util.stream.Stream;

public class Axis {
    private final static Logger logger= LoggerFactory.getLogger(Axis.class);
    private final static boolean VERBOSE;

    static {
        String s = System.getProperty("io.crowds.proxy.ddnsp.log.routeInfo");
        VERBOSE = "verbose".equalsIgnoreCase(s);
    }

    private final Context context;
    private final ChannelCreator channelCreator;

    private ProxyOption proxyOption;

    private FakeDns fakeDns;

    private HostnameSniffer hostnameSniffer;

    private final NatMappings natMappings;

    private Router router;

    private TransportProvider transportProvider;

    private final UdpMappings mappings;

    public Axis(Context context) {
        this.context = context;
        this.channelCreator = new ChannelCreator(context);
        this.natMappings = new NatMappings();
        this.mappings=new UdpMappings();
    }

    public static class NatMappings{
        private volatile JsonObject preNatConfig;
        private volatile NatMap preNat;
        private volatile JsonObject postNatConfig;
        private volatile Map<String,NatMap> postNats;

        NatMap buildNatMap(JsonObject natConfig){
            NatMap natMap = new NatMap();
            for (Map.Entry<String, Object> entry : natConfig) {
                String pattern = entry.getKey();
                Object result = entry.getValue();
                if (!(result instanceof String)){
                    continue;
                }
                natMap.add(pattern, (String) result);
            }
            return natMap;
        }

        NatMap buildPreNat(JsonObject natConfig){
            if (natConfig.isEmpty()){
                return null;
            }
            NatMap preNat = this.preNat;
            if (preNat==null){
                return buildNatMap(natConfig);
            }else{
                if (!Objects.equals(preNatConfig,natConfig)){
                    return buildNatMap(natConfig);
                }
                return preNat;
            }
        }

        Map<String,NatMap> buildPostNats(JsonObject proxiesConfig){
            if (proxiesConfig==null||proxiesConfig.isEmpty()){
                return null;
            }
            Map<String, NatMap> postNats = this.postNats;
            if (postNats==null){
                return proxiesConfig.stream().filter(it->it.getValue() instanceof JsonObject)
                                    .collect(Collectors.toMap(Map.Entry::getKey, it->buildNatMap((JsonObject) it.getValue())));
            }else{
                if (!Objects.equals(this.postNatConfig,proxiesConfig)){
                    return proxiesConfig.stream().filter(it->it.getValue() instanceof JsonObject)
                                        .collect(Collectors.toMap(Map.Entry::getKey, it->buildNatMap((JsonObject) it.getValue())));
                }
                return postNats;
            }
        }

        synchronized void setNatConfig(JsonObject natConfig){
            if (natConfig==null){
                logger.info("nat mapping discard.");
                this.preNatConfig = null;
                this.preNat = null;
                this.postNatConfig = null;
                this.postNats = null;
                return;
            }
            Object json = natConfig.remove("proxies");
            if (json!=null&&!(json instanceof JsonObject)){
                logger.error("Config: proxy.nat.proxies is not a JsonObject");
                return;
            }
            JsonObject postNatConfig = json==null? null : (JsonObject) json;
            NatMap natMap = buildPreNat(natConfig);
            Map<String, NatMap> postNats = buildPostNats(postNatConfig);
            if (natMap!=this.preNat) {
                this.preNat = natMap;
                this.preNatConfig = natConfig;
                logger.info("pre route nat mapping setup.");
            }
            if (postNats!=this.postNats) {
                this.postNats = postNats;
                this.postNatConfig = postNatConfig;
                logger.info("post route nat mapping setup.");
            }

        }

    }

    public Axis setProxyOption(ProxyOption proxyOption) {

        SniffOption sniff = proxyOption.getSniff();
        if (this.hostnameSniffer==null||!Objects.equals(this.hostnameSniffer.getOption(), sniff)){
            this.hostnameSniffer = sniff==null?null:new HostnameSniffer(sniff);
        }

        if (proxyOption.getNat()!=null){
            this.natMappings.setNatConfig(proxyOption.getNat());
        }else{
            this.natMappings.setNatConfig(null);
        }

        if (proxyOption.getRules()!=null){
            if (this.proxyOption==null||!proxyOption.getRules().equals(this.proxyOption.getRules())) {
                this.router = new CachedRouter(proxyOption.getRules(),2,12,12);
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




    private Transport lookupTransport(ProxyContext proxyContext){
        NatMap preRouteNat = this.natMappings.preNat;
        Map<String, NatMap> postRouteNats = this.natMappings.postNats;
        Router router = this.router;
        NetLocation netLocation = proxyContext.getNetLocation();
        if (preRouteNat != null){
            NetAddr addr = preRouteNat.translate(netLocation.getDst());
            if (addr!=null){
                if (logger.isDebugEnabled())
                    logger.debug("pre route nat {} -> {}",netLocation.getDst(),addr);
                netLocation = new NetLocation(netLocation.getSrc(),addr,netLocation.getTp());
                proxyContext.withNetLocation(netLocation);
            }
        }
        if (router ==null){
            return transportProvider.direct();
        }

        String tag = router.routing(netLocation);
        proxyContext.withTag(tag);

        Transport transport = transportProvider.getTransport(proxyContext);

        if (postRouteNats!=null) {
            NatMap natMap = postRouteNats.get(transport.proxy().getTag());
            NetAddr addr = natMap.translate(netLocation.getDst());
            if (addr!=null){
                if (logger.isDebugEnabled())
                    logger.debug("post route nat {} -> {}",netLocation.getDst(),addr);
                netLocation = new NetLocation(netLocation.getSrc(),addr,netLocation.getTp());
                proxyContext.withNetLocation(netLocation);
            }
        }

        return transport;
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

    private void logException(SocketAddress srcAddr,SocketAddress dstAddr,Throwable t,boolean src){
        if (Exceptions.isExpected(t)){
            if (Exceptions.shouldLogMessage(t)) {
                logger.error("{}->{} caught exception from {}: {}",srcAddr,dstAddr,src?"src":"dst",t.getMessage());
            }
        }else{
            logger.error("{}->{} caught exception from {}",srcAddr,dstAddr,src?"src":"dst",t);
        }
    }

    private void logRouteInfo(ProxyContext proxyContext,Transport transport){
        NetLocation netLocation = proxyContext.getNetLocation();
        String tp = netLocation.getTp() == TP.TCP ? "tcp" : "udp";
        if (VERBOSE){
            List<NetLocation> locations = Objects.requireNonNullElse(proxyContext.getPrevLocations(),List.of());
            String src = Stream.concat(locations.stream().map(NetLocation::getSrc),Stream.of(netLocation.getSrc()))
                               .map(Object::toString).distinct().collect(Collectors.joining("=>"));
            String dst = Stream.concat(locations.stream().map(NetLocation::getDst),Stream.of(netLocation.getDst()))
                               .map(Object::toString).distinct().collect(Collectors.joining("=>"));
            logger.info("{} [{}] to [{}] via [{}]", tp,src,dst, transport.getChain());
        }else{
            logger.info("{} {} to {} via [{}]", tp,netLocation.getSrc(),netLocation.getDst(), transport.getChain());
        }

    }


    private Future<ProxyContext> createTcpContext(Channel channel, NetLocation netLocation){
        EventLoop eventLoop = channel.eventLoop();

        ProxyContext proxyContext = new ProxyContext(eventLoop,netLocation);

        FakeContext fakeContext=getFakeContext(netLocation.getDst());
        if (fakeContext!=null) {
            proxyContext.withFakeContext(fakeContext);
            proxyContext.withNetLocation(new NetLocation(netLocation.getSrc(), fakeContext.getNetAddr(netLocation.getDst().getPort()), netLocation.getTp()));
            return eventLoop.newSucceededFuture(proxyContext);
        }
        if (netLocation.getDst() instanceof DomainNetAddr){
            return eventLoop.newSucceededFuture(proxyContext);
        }
        HostnameSniffer sniffer = this.hostnameSniffer;
        if (sniffer==null){
            return eventLoop.newSucceededFuture(proxyContext);
        }

        Promise<ProxyContext> promise = channel.eventLoop().newPromise();

        sniffer.sniff(channel,netLocation)
               .addListener(f->{
                   if (f.isSuccess()){
                       String hostname = (String) f.get();
                       proxyContext.withNetLocation(new NetLocation(netLocation.getSrc(), NetAddr.of(hostname, netLocation.getDst().getPort()), netLocation.getTp()));
                   }
                   promise.setSuccess(proxyContext);
               });
        return promise;
    }

    public Promise<Void> handleTcp(Channel channel,SocketAddress srcAddr,SocketAddress dstAddr){
        Promise<Void> promise = channel.eventLoop().newPromise();
        channel.config().setAutoRead(false);

        NetLocation netLocation = new NetLocation(getNetAddr(srcAddr),getNetAddr(dstAddr), TP.TCP);
        createTcpContext(channel,netLocation)
                .addListener(f->{
                    assert f.isSuccess();
                    try {
                        ProxyContext proxyContext = (ProxyContext) f.get();
                        TcpEndPoint src = new TcpEndPoint(channel);
                        src.exceptionHandler(t->logException(srcAddr,dstAddr,t,true));
                        Transport transport= lookupTransport(proxyContext);
                        logRouteInfo(proxyContext,transport);
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
                    } catch (Exception e) {
                        logger.error("",e);
                        promise.tryFailure(e);
                        channel.close();
                    }

                });

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
                Transport transport= lookupTransport(proxyContext);
                logRouteInfo(proxyContext,transport);
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
