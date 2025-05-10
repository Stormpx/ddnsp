package io.crowds.proxy.transport.proxy.wireguard;

import io.crowds.Context;
import io.crowds.Ddnsp;
import io.crowds.compoments.dns.VariantResolver;
import io.crowds.dns.DnsCli;
import io.crowds.dns.UdpUpstream;
import io.crowds.dns.cache.DnsCache;
import io.crowds.proxy.Axis;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.TP;
import io.crowds.proxy.common.DynamicRecipientLookupHandler;
import io.crowds.proxy.transport.Transport;
import io.crowds.proxy.transport.proxy.FullConeProxyTransport;
import io.crowds.util.Pkts;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stormpx.net.PartialNetStack;
import org.stormpx.net.RouteItem;
import org.stormpx.net.netty.*;
import org.stormpx.net.network.IfType;
import org.stormpx.net.network.NetworkParams;
import org.stormpx.net.socket.PartialSocketOptions;
import org.stormpx.net.util.IP;
import org.stormpx.net.util.IPv4;
import org.stormpx.net.util.IPv6;
import org.stormpx.net.util.SubNet;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Comparator;
import java.util.List;

public class WireguardProxyTransport extends FullConeProxyTransport {
    private static final Logger logger = LoggerFactory.getLogger(WireguardProxyTransport.class);
    private final WireguardOption wireguardOption;
    private final PartialNetStack netStack;
    private final EventLoopGroup eventLoopGroup;
    private final VariantResolver variantResolver;

    private WireguardIface iface;
    private List<Peer> peers;

    public WireguardProxyTransport(Axis axis, WireguardOption wireguardOption) {
        super(axis.getChannelCreator(), wireguardOption);
        this.wireguardOption = wireguardOption;
        this.netStack = new PartialNetStack();
        this.eventLoopGroup = new MultiThreadIoEventLoopGroup(1,PartialIoHandler.newFactory(netStack, NioIoHandler.newFactory()));

        if (wireguardOption.getDns()!=null) {
            var upstream = new UdpUpstream(eventLoopGroup.next(), new PartialDatagramChannel(), wireguardOption.getDns());
            var dnsCli = new DnsCli(eventLoopGroup, new DnsCache(eventLoopGroup.next()), upstream, wireguardOption.getAddress().address() instanceof IPv6);
            this.variantResolver = new VariantResolver(()->dnsCli);
        }else{
            this.variantResolver = new VariantResolver(Ddnsp::dnsResolver);
        }

        initNetStack(axis.getContext());
    }

    private void initNetStack(Context context){
        PartialNetStack netStack = this.netStack;

        WireguardIface iface = new WireguardIface();
        List<Peer> peers = wireguardOption.getPeers().stream()
                                          .map(option->new Peer(context.getEventLoopGroup().next(), wireguardOption.getPrivateKey(), option))
                                          .toList();
        iface.packetHandler(packet->{
            //write to network
            byte[] bytes = Pkts.getDestinationAddress(packet);
            if (bytes==null){
                return;
            }
            try {
                InetAddress address = InetAddress.getByAddress(bytes);
                peers.stream()
                     .filter(it -> it.match(address)).max(Comparator.comparing(Peer::mask))
                     .ifPresent(peer -> peer.write(packet));
            } catch (UnknownHostException e) {
                //ignore
            } catch (Exception e){
                logger.error("",e);
            }
        });
        for (Peer peer : peers) {
            peer.setTransport(transport);
            peer.networkPacketHandler(iface::writeToStack);
        }

        SubNet ipcidr = wireguardOption.getAddress();
        IP ip = ipcidr.address();
        NetworkParams networkParams = new NetworkParams();
        networkParams.setMtu(3000)
                     .setSubNet(ipcidr)
                     .setIfType(IfType.INTERNET)
                     .addIp(ip);
        netStack.addNetwork(wireguardOption.getName(),networkParams,()->iface);
        netStack.addRoute(new RouteItem().setDestination(new SubNet(IPv4.UNSPECIFIED,0)).setNetwork(wireguardOption.getName()));

        this.iface = iface;
        this.peers = peers;
    }


    public WireguardProxyTransport setTransport(Transport transport) {
        this.transport = transport;
        for (Peer peer : this.peers) {
            peer.setTransport(transport);
        }
        return this;
    }

    @Override
    public String getTag() {
        return wireguardOption.getName();
    }


    public Future<Channel> createChannel(EventLoop eventLoop, NetLocation netLocation, Transport ignore) throws Exception {
        Promise<Channel> promise = eventLoop.newPromise();
        Class<? extends AbstractPartialChannel> klass = netLocation.getTp()== TP.TCP? PartialSocketChannel.class: PartialDatagramChannel.class;
        var bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .resolver(variantResolver.getNettyResolver())
                .channel(klass)
                .handler(new DynamicRecipientLookupHandler(variantResolver.getInternalDnsResolver()));
        if (netLocation.getTp()==TP.TCP){
            bootstrap.option(PartialChannelOption.of(PartialSocketOptions.TCP_CONNECT_TIMEOUT),30000);
        }
        var cf=  bootstrap.connect(netLocation.getDst().getAddress(),new InetSocketAddress(InetAddress.getByAddress(wireguardOption.getAddress().address().getBytes()),0));
        cf.addListener(f->{
            if (!f.isSuccess()){
                promise.tryFailure(f.cause());
            }
            promise.trySuccess(cf.channel());
        });
        return promise;
    }



}
