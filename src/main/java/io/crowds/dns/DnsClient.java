package io.crowds.dns;


import io.crowds.dns.cache.CacheKey;
import io.crowds.dns.cache.DnsCache;
import io.crowds.util.AddrType;
import io.crowds.util.Inet;
import io.crowds.util.Strs;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.dns.*;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class DnsClient implements InternalDnsResolver{
    private final Logger logger= LoggerFactory.getLogger(DnsClient.class);
    private Vertx vertx;
    private EventLoopGroup eventLoopGroup;
    private boolean tryIpv6;
    private DnsCache dnsCache;

    private DnsUpstream defaultStream;
    private List<DnsUpstream> upStreams;


    public DnsClient(Vertx vertx, ClientOption option) {
        this.vertx=vertx;
        this.eventLoopGroup= vertx.nettyEventLoopGroup();
        this.tryIpv6= option.isTryIpv6();
        this.dnsCache=new DnsCache(eventLoopGroup.next());
        newDefaultStream();
        newUpStreams(option.getUpstreams());
    }

    private void newDefaultStream(){
        String server = System.getProperty("ddnsp.dns.default.server");
        if (Strs.isBlank(server)){
            server=Locale.getDefault()==Locale.CHINA?"114.114.114.114:53":"8.8.8.8:53";
        }
        InetSocketAddress address = Inet.parseInetAddress(server);
        this.defaultStream = new UdpUpstream(eventLoopGroup,address);
    }


    private void newUpStreams(List<URI> dnsServers){
        if (dnsServers==null){
            this.upStreams=List.of();
            return;
        }
        this.upStreams=dnsServers.stream()
                .map(uri->{
                    String scheme = uri.getScheme();
                    return switch (Strs.isBlank(scheme)?"dns":scheme) {
                        case "dns", "udp" -> new UdpUpstream(eventLoopGroup, Inet.createSocketAddress(uri.getHost(), uri.getPort()));
                        case "http", "https" -> new DohUpstream(vertx, uri,this);
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

    private DnsQuery newQuery(String name,DnsRecordType type){
        name = name.endsWith(".")?name:name+".";
        return new DefaultDnsQuery(0,DnsOpCode.QUERY)
                .setRecursionDesired(true)
                .addRecord(DnsSection.QUESTION,new DefaultDnsQuestion(name,type));
    }

    private DnsResponse copyResp(DnsResponse response){
        if (response instanceof SafeDnsResponse){
            return response;
        }
        DefaultDnsResponse dnsResponse = new SafeDnsResponse(response.id(), response.opCode(), response.code());
        DnsKit.msgCopy(response,dnsResponse,true);
        ReferenceCountUtil.safeRelease(response);
        return dnsResponse;
    }

    private Future<DnsResponse> scheduleUpStreams(DnsQuery dnsQuery){
        return CompositeFuture.any(
                this.upStreams
                        .stream()
                        .map(upStreams->upStreams.lookup(dnsQuery).map(this::copyResp)
                                                 .onFailure(e->{
                                                     if (logger.isDebugEnabled()){
                                                         logger.error("{}",e.getMessage(),e);
                                                     }
                                                 }))
                        .collect(Collectors.toList())
        ).compose(cf -> IntStream.range(0,cf.size())
                .filter(cf::succeeded)
                .mapToObj(cf::<DnsResponse>resultAt)
                .findFirst()
                .map(Future::succeededFuture)
                .orElseGet(()->Future.failedFuture("no available upstream."))
        );
    }


    private void tryCacheResponse(DnsResponse response){
        if (response.code()==DnsResponseCode.NOERROR&&!response.isTruncated()){
            dnsCache.cacheMessage(response, eventLoopGroup.next());
        }
    }

    private Future<DnsResponse> request(DnsQuery dnsQuery,boolean useDefault){
        if (this.upStreams.isEmpty()){
            useDefault=true;
        }

        DnsRecord record = dnsQuery.recordAt(DnsSection.QUESTION, 0);

        DomainLookupEvent event = new DomainLookupEvent(record.name(),record.type().toString(),useDefault);
        event.begin();

        CacheKey key = new CacheKey(record);
        var result = new ArrayList<DnsRecord>();
        if (dnsCache.getAnswer(key, dnsQuery.isRecursionDesired(), result)){
            SafeDnsResponse response = new SafeDnsResponse(dnsQuery.id(), dnsQuery.opCode(), DnsResponseCode.NOERROR);
            response.setRecursionDesired(dnsQuery.isRecursionDesired());
            response.addRecord(DnsSection.QUESTION,DnsKit.clone(record));
            for (DnsRecord dnsRecord : result) {
                response.addRecord(DnsSection.ANSWER,dnsRecord);
            }
            event.hitCacheCommit();
            return Future.succeededFuture(response);
        }

        var future =useDefault?this.defaultStream.lookup(dnsQuery).map(this::copyResp):scheduleUpStreams(dnsQuery);

        return future.onComplete(event::commit)
                     .onSuccess(this::tryCacheResponse);

    }



    private Future<List<InetAddress>> request(String target,DnsRecordType type,boolean useDefault){
        if (this.upStreams.isEmpty()){
            useDefault=true;
        }

        DomainLookupEvent event = new DomainLookupEvent(target,type.toString(),useDefault);
        event.begin();

        CacheKey key = new CacheKey(target,type);
        List<InetAddress> inetAddresses = dnsCache.lightWeightGet(key, true);
        if (!inetAddresses.isEmpty()){
            event.hitCacheCommit();
            return Future.succeededFuture(inetAddresses);
        }

        DnsQuery dnsQuery = newQuery(target, type);
        var future =useDefault?this.defaultStream.lookup(dnsQuery).map(this::copyResp):scheduleUpStreams(dnsQuery);

        return future.onComplete(event::commit)
                     .onSuccess(this::tryCacheResponse)
                     .map(resp->DnsKit.getInetAddrFromResponse(resp,type==DnsRecordType.A).toList());
    }


    public Future<DnsResponse> request(DnsQuery dnsQuery){
        return request(dnsQuery,false);
    }

    public Future<List<InetAddress>> requestAll(String target, DnsRecordType type, boolean useDefault){
        return request(target,type,useDefault)
                .compose(it->it==null?Future.failedFuture(new UnknownHostException("resolve %s type %s failed".formatted(target,type.name()))):Future.succeededFuture(it));
    }
    public Future<List<InetAddress>> requestAll(String target, boolean useDefault){
        List<Future> fs=(Inet.isSupportsIpV6()?Stream.of(DnsRecordType.A,DnsRecordType.AAAA):Stream.of(DnsRecordType.A))
                .map(type -> requestAll(target,type,useDefault))
                .collect(Collectors.toList());
        return CompositeFuture.any(fs)
                .map(cf-> IntStream.range(0,cf.size())
                                   .filter(cf::succeeded)
                                   .mapToObj(cf::<List<InetAddress>>resultAt)
                                   .flatMap(Collection::stream)
                                   .toList()
                );
    }

    public Future<InetAddress> requestIp(String target, DnsRecordType type, boolean useDefault){
        return request(target,type,useDefault)
                .map(list->list.isEmpty()?null:list.get(0))
                .compose(it->it==null?Future.failedFuture(new UnknownHostException("resolve %s type %s failed".formatted(target,type.name()))):Future.succeededFuture(it));

    }

    public Future<InetAddress> requestIp(String target, boolean useDefault){
        List<Future> fs=(Inet.isSupportsIpV6()?Stream.of(DnsRecordType.A,DnsRecordType.AAAA):Stream.of(DnsRecordType.A))
                .map(type -> requestIp(target,type,useDefault))
                .collect(Collectors.toList());
        return CompositeFuture.any(fs)
                .compose(cf-> IntStream.range(0,cf.size())
                        .filter(cf::succeeded)
                        .mapToObj(cf::<InetAddress>resultAt)
                        .findFirst()
                        .map(Future::succeededFuture)
                        .orElseGet(()->Future.failedFuture("upstream all failed.")));
    }

    @Override
    public Future<InetAddress> bootResolve(String host,AddrType addrType) {
        if (addrType==null){
            if (tryIpv6){
                return requestIp(host,true);
            }
            addrType=AddrType.IPV4;
        }

        return switch (addrType){
            case IPV4 -> requestIp(host,DnsRecordType.A,true);
            case IPV6 -> requestIp(host,DnsRecordType.AAAA,true);
        };
    }

    @Override
    public Future<InetAddress> resolve(String host, AddrType addrType) {
        if (addrType==null){
            if (tryIpv6){
                return requestIp(host,false);
            }
            addrType=AddrType.IPV4;
        }
        return switch (addrType){
            case IPV4 -> requestIp(host,DnsRecordType.A,false);
            case IPV6 -> requestIp(host,DnsRecordType.AAAA,false);
        };
    }
}
