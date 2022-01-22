package io.crowds.proxy;

import io.crowds.proxy.dns.FakeContext;
import io.crowds.proxy.dns.FakeDns;
import io.crowds.proxy.dns.FakeOption;
import io.crowds.proxy.routing.Router;
import io.crowds.proxy.select.TransportProvider;
import io.crowds.proxy.select.Transport;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.direct.TcpEndPoint;
import io.crowds.proxy.transport.direct.UdpEndPoint;
import io.crowds.util.IPCIDR;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;

public class Axis {
    private final static Logger logger= LoggerFactory.getLogger(Axis.class);
    private final static String DEFAULT_TRANSPORT="direct";
    private final static String BLOCK_TRANSPORT="block";
    private EventLoopGroup eventLoopGroup;
    private ChannelCreator channelCreator;
    private ProxyOption proxyOption;

    private FakeDns fakeDns;

    private Router router;

    private TransportProvider transportProvider;
//    private Map<String,TransportProvider> providerMap;

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
        if (this.transportProvider==null){
            this.transportProvider =new TransportProvider(channelCreator,proxyOption.getProxies());
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

    }

    public FakeDns getFakeDns() {
        return fakeDns;
    }


    private Transport getTransport(ProxyContext proxyContext){
        if (this.router==null){
            return transportProvider.direct();
        }
        if (proxyContext.getFakeContext()!=null){
            return transportProvider.getTransport(proxyContext);
        }

        NetLocation netLocation = proxyContext.getNetLocation();
        String tag = this.router.routing(netLocation);
        proxyContext.withTag(tag);

        return transportProvider.getTransport(proxyContext);

    }


    private NetAddr getNetAddr(SocketAddress address){
        if (address instanceof InetSocketAddress inetAddr){
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
            Transport transport=getTransport(proxyContext);
            logger.info("tcp {} to {} via [{}]",proxyContext.getNetLocation().getSrc(),proxyContext.getNetLocation().getDest(), transport.chain());
            ProxyTransport proxy = transport.proxy();
            proxy.createEndPoint(proxyContext)
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
            Transport transport=getTransport(proxyContext);
            logger.info("udp {} to {} via [{}]",proxyContext.getNetLocation().getSrc(),proxyContext.getNetLocation().getDest(),transport.chain());
            ProxyTransport proxy = transport.proxy();
            proxy.createEndPoint(proxyContext)
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
