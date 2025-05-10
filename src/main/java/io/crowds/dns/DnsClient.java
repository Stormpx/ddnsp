package io.crowds.dns;


import io.crowds.Context;
import io.crowds.compoments.dns.InternalDnsResolver;
import io.crowds.dns.cache.DnsCache;
import io.crowds.util.*;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.dns.*;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.*;
import java.util.stream.Collectors;

public class DnsClient implements InternalDnsResolver {
    private final Logger logger= LoggerFactory.getLogger(DnsClient.class);
    private final EventLoopGroup eventLoopGroup;

    private final DatagramChannelFactory datagramChannelFactory;
    private final DnsCache dnsCache;

    private final DnsCli dnsCli;


    public DnsClient(Context context, ClientOption option) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(option);
        this.eventLoopGroup= context.getEventLoopGroup();
        this.datagramChannelFactory = context.getChannelFactoryProvider().getDatagramChannelFactory();
        this.dnsCache=new DnsCache(eventLoopGroup.next());
        var defaultStream = newDefaultUpstream();
        var upStreams = newUpStreams(context,option.getUpstreams());
        this.dnsCli = new DnsCli(eventLoopGroup,this.dnsCache,defaultStream,upStreams, option.isTryIpv6()&&Inet.isSupportsIpV6());
    }
    public DnsClient(EventLoopGroup eventLoopGroup, DatagramChannelFactory datagramChannelFactory) {
        Objects.requireNonNull(eventLoopGroup);
        Objects.requireNonNull(datagramChannelFactory);
        this.eventLoopGroup = eventLoopGroup;
        this.datagramChannelFactory = datagramChannelFactory;
        this.dnsCache=new DnsCache(eventLoopGroup.next());
        var defaultStream = newDefaultUpstream();
        this.dnsCli = new DnsCli(eventLoopGroup,this.dnsCache,defaultStream, Inet.isSupportsIpV6());
    }

    private UdpUpstream newUdpUpstream(InetSocketAddress address){
        if (address.isUnresolved()){
            return null;
        }
        var channel = datagramChannelFactory.newChannel(AddrType.of(address.getAddress()));
        return new UdpUpstream(eventLoopGroup.next(),channel,address);
    }

    private UdpUpstream newDefaultUpstream(){
        String server = System.getProperty("ddnsp.dns.default.server");
        if (Strs.isBlank(server)){
            server="8.8.8.8:53";
        }
        InetSocketAddress address = Inet.parseInetAddress(server);
        return newUdpUpstream(address);
    }

    private List<DnsUpstream> newUpStreams(Context context,List<URI> dnsServers){
        if (dnsServers==null){
            return List.of();
        }
        return dnsServers.stream()
                .map(uri->{
                    String scheme = uri.getScheme();
                    return switch (Strs.isBlank(scheme)?"dns":scheme) {
                        case "dns", "udp" -> newUdpUpstream(Inet.createSocketAddress(uri.getHost(), uri.getPort()));
                        case "http", "https" -> new DohUpstream(context.getVertx(), uri,this);
                        default -> {
                            logger.error("unsupported dns server {}",uri);
                            yield null;
                        }
                    };
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public void invalidateCache(){
        this.dnsCache.invalidateAll();
    }


    public Future<DnsResponse> request(DnsQuery dnsQuery){
        return dnsCli.request(dnsQuery);
    }

    @Override
    public Future<List<InetAddress>> bootResolveAll(String host, AddrType addrType) {
        return dnsCli.bootResolveAll(host, addrType);
    }
    @Override
    public Future<InetAddress> bootResolve(String host,AddrType addrType) {
        return dnsCli.bootResolve(host, addrType);
    }

    @Override
    public Future<List<InetAddress>> resolveAll(String host, AddrType addrType) {
        return dnsCli.resolveAll(host, addrType);
    }
    @Override
    public Future<InetAddress> resolve(String host, AddrType addrType) {
        return dnsCli.resolve(host, addrType);
    }
}
