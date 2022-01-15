package io.crowds.proxy;

import io.crowds.proxy.dns.FakeContext;
import io.crowds.proxy.dns.FakeDns;
import io.crowds.proxy.dns.FakeOption;
import io.crowds.proxy.routing.Router;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.block.BlockProxyTransport;
import io.crowds.proxy.transport.direct.DirectProxyTransport;
import io.crowds.proxy.transport.direct.TcpEndPoint;
import io.crowds.proxy.transport.direct.UdpEndPoint;
import io.crowds.proxy.transport.shadowsocks.ShadowsocksOption;
import io.crowds.proxy.transport.shadowsocks.ShadowsocksTransport;
import io.crowds.proxy.transport.vmess.VmessOption;
import io.crowds.proxy.transport.vmess.VmessProxyTransport;
import io.crowds.util.IPCIDR;
import io.netty.channel.Channel;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Axis {
    private final static Logger logger= LoggerFactory.getLogger(Axis.class);
    private final static String DEFAULT_TRANSPORT="direct";
    private final static String BLOCK_TRANSPORT="block";
    private EventLoopGroup eventLoopGroup;
    private ChannelCreator channelCreator;
    private ProxyOption proxyOption;

    private FakeDns fakeDns;

    private Router router;
    private Map<String,TransportProvider> providerMap;

    public Axis(EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        this.channelCreator = new ChannelCreator(eventLoopGroup);
    }

    public Axis setProxyOption(ProxyOption proxyOption) {

        if (proxyOption.getRules()!=null){
            if (this.proxyOption==null||!proxyOption.getRules().equals(this.proxyOption.getRules())) {
                this.router = new Router(proxyOption.getRules());
                if (this.fakeDns != null) this.fakeDns.setRouter(router);
                logger.info("router rules setup.");
            }
        }
        if (this.providerMap==null){
            initProvider(proxyOption);
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
                    throw new IllegalArgumentException("expect ipv4 cidr");
                }
            }
            this.fakeDns=new FakeDns(eventLoopGroup.next(), router,ipv4Cidr,ipv6Cidr,fakeOption.getDestStrategy());
            logger.info("fake dns setup.");
        } catch (Exception e) {
            logger.warn("failed to create fakeDns cause:{}",e.getMessage());
        }

    }

    private void initProvider(ProxyOption proxyOption){
        var map=new ConcurrentHashMap<String,TransportProvider>();

        map.put(DEFAULT_TRANSPORT,new DirectProxyTransport(eventLoopGroup,channelCreator));
        map.put(BLOCK_TRANSPORT,new BlockProxyTransport(eventLoopGroup,channelCreator));

        if (proxyOption.getProxies()==null)
            return;
        for (ProtocolOption protocolOption : proxyOption.getProxies()) {
            if ("vmess".equalsIgnoreCase(protocolOption.getProtocol())){
                map.put(protocolOption.getName(),new VmessProxyTransport((VmessOption) protocolOption,eventLoopGroup,channelCreator));
            }else if ("ss".equalsIgnoreCase(protocolOption.getProtocol())){
                map.put(protocolOption.getName(),new ShadowsocksTransport(eventLoopGroup,channelCreator, (ShadowsocksOption) protocolOption));
            }
        }

        this.providerMap=map;

    }

    public FakeDns getFakeDns() {
        return fakeDns;
    }

    private TransportProvider getProvider(ProxyContext proxyContext){
        if (this.router==null){
            return providerMap.get(DEFAULT_TRANSPORT);
        }
        if (proxyContext.getFakeContext()!=null){
            TransportProvider provider = providerMap.get(proxyContext.getFakeContext().getTag());
            if (provider!=null)
                return provider;
        }

        NetLocation netLocation = proxyContext.getNetLocation();
        String tag = this.router.routing(netLocation);
        if (tag==null){
            return providerMap.get(DEFAULT_TRANSPORT);
        }
        TransportProvider provider = providerMap.get(tag);
        if (provider==null){
            return providerMap.get(DEFAULT_TRANSPORT);
        }
        return provider;

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

    private FakeContext getFakeContext(NetAddr netAddr){
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

    public void handleTcp(Channel channel,SocketAddress srcAddr,SocketAddress destAddr){
        try {

            TcpEndPoint src = new TcpEndPoint(channel)
                    .exceptionHandler(e->logger.info("src {} caught exception :{}",channel.remoteAddress(),e.getMessage()));


            NetLocation netLocation = new NetLocation(getNetAddr(srcAddr),getNetAddr(destAddr), TP.TCP);

            ProxyContext proxyContext = new ProxyContext(channel.eventLoop(),netLocation);

            if (fakeDns!=null){
                proxyContext.withFakeContext(getFakeContext(netLocation.getDest()));
                netLocation=proxyContext.getNetLocation();
            }
            TransportProvider provider = getProvider(proxyContext);
            logger.info("tcp {} to {} via [{}]",proxyContext.getNetLocation().getSrc(),proxyContext.getNetLocation().getDest(),provider.getTag());
            ProxyTransport transport = provider.getTransport(proxyContext);
            transport.createEndPoint(proxyContext)
                    .addListener(future -> {
                        if (!future.isSuccess()){
                            if (logger.isDebugEnabled())
                                logger.error("",future.cause());
                            logger.error("failed to connect remote: {} > {}",proxyContext.getNetLocation().getDest().getAddress(),future.cause().getMessage());
                            src.close();
                            return;
                        }
                        EndPoint dest= (EndPoint) future.get();
                        proxyContext.bridging(src,dest);

                    });
        } catch (Exception e) {
            e.printStackTrace();
            channel.close();
        }
    }

    public void handleUdp(DatagramChannel datagramChannel,DatagramPacket packet){
        try {
            InetSocketAddress recipient = packet.recipient();
            InetSocketAddress sender = packet.sender();
            var src=new UdpEndPoint(datagramChannel,sender);
            NetLocation netLocation = new NetLocation(getNetAddr(sender), getNetAddr(recipient), TP.UDP);
            ProxyContext proxyContext = new ProxyContext(datagramChannel.eventLoop(),netLocation);
            if (fakeDns!=null){
                proxyContext.withFakeContext(getFakeContext(netLocation.getDest()));
                netLocation=proxyContext.getNetLocation();
            }
            TransportProvider provider = getProvider(proxyContext);
            logger.info("udp {} to {} via [{}]",proxyContext.getNetLocation().getSrc(),proxyContext.getNetLocation().getDest(),provider.getTag());
            ProxyTransport transport = provider.getTransport(proxyContext);
            transport.createEndPoint(proxyContext)
                    .addListener(future -> {
                        if (!future.isSuccess()){
                            if (logger.isDebugEnabled())
                                logger.error("",future.cause());
                            ReferenceCountUtil.safeRelease(packet);
                            return;
                        }
                        EndPoint dest= (EndPoint) future.get();

                        proxyContext.bridging(src,dest);

                        dest.write(packet.content());
                    });
        } catch (Exception e) {
            ReferenceCountUtil.safeRelease(packet);
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
