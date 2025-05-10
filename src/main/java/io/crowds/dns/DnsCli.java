package io.crowds.dns;

import io.crowds.compoments.dns.InternalDnsResolver;
import io.crowds.dns.cache.CacheKey;
import io.crowds.dns.cache.DnsCache;
import io.crowds.util.AddrType;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.dns.*;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class DnsCli implements InternalDnsResolver {
    private final Logger logger= LoggerFactory.getLogger(DnsClient.class);
    private final EventLoopGroup eventLoopGroup;
    private final DnsCache dnsCache;
    private final DnsUpstream defaultStream;
    private final List<DnsUpstream> upStreams;
    private final boolean useIPV6;


    public DnsCli(EventLoopGroup eventLoopGroup, DnsCache dnsCache, DnsUpstream defaultStream, List<DnsUpstream> upStreams, boolean useIPV6) {
        this.eventLoopGroup = eventLoopGroup;
        this.dnsCache = dnsCache;
        this.defaultStream = defaultStream;
        this.upStreams = upStreams;
        this.useIPV6 = useIPV6;
    }

    public DnsCli(EventLoopGroup eventLoopGroup, DnsCache dnsCache, DnsUpstream dnsUpStream, boolean useIPV6) {
        this(eventLoopGroup,dnsCache,dnsUpStream,List.of(dnsUpStream),useIPV6);
    }


    private DnsQuery newQuery(String name,DnsRecordType type){
        if (!name.endsWith(".")) {
            name += ".";
        }
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
        return Future.any(
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


    public Future<DnsResponse> request(DnsQuery dnsQuery){
        return request(dnsQuery,false);
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

    private Future<List<InetAddress>> requestAll(String target, DnsRecordType type, boolean useDefault){
        return request(target,type,useDefault)
                .expecting(Objects::nonNull)
                .compose(Future::succeededFuture, t->Future.failedFuture(new RuntimeException("resolve %s type %s failed".formatted(target,type.name()),t)));
    }
    private Future<List<InetAddress>> requestAll(String target, boolean useDefault){
        List<Future<?>> fs=(useIPV6?Stream.of(DnsRecordType.A,DnsRecordType.AAAA):Stream.of(DnsRecordType.A))
                .map(type -> requestAll(target,type,useDefault))
                .collect(Collectors.toList());
        return Future.any(fs)
                     .map(cf-> IntStream.range(0,cf.size())
                                        .filter(cf::succeeded)
                                        .mapToObj(cf::<List<InetAddress>>resultAt)
                                        .flatMap(Collection::stream)
                                        .toList()
                     );
    }

    private Future<InetAddress> requestIp(String target, DnsRecordType type, boolean useDefault){
        return request(target,type,useDefault)
                .map(list->list.isEmpty()?null:list.getFirst())
                .expecting(Objects::nonNull)
                .compose(Future::succeededFuture, t->Future.failedFuture(new RuntimeException("resolve %s type %s failed".formatted(target,type.name()),t)));
    }

    private Future<InetAddress> requestIp(String target, boolean useDefault){
        List<Future<?>> fs=(useIPV6?Stream.of(DnsRecordType.A,DnsRecordType.AAAA):Stream.of(DnsRecordType.A))
                .map(type -> requestIp(target,type,useDefault))
                .collect(Collectors.toList());
        return Future.any(fs)
                     .compose(cf-> IntStream.range(0,cf.size())
                                            .filter(cf::succeeded)
                                            .mapToObj(cf::<InetAddress>resultAt)
                                            .findFirst()
                                            .map(Future::succeededFuture)
                                            .orElseGet(()->Future.failedFuture("upstream all failed.")));
    }

    private Future<List<InetAddress>> doResolve(String host, AddrType addrType,boolean useDefault,boolean resolveAll){
        if (addrType==null){
            if (useIPV6){
                if (resolveAll){
                    return requestAll(host,useDefault);
                }else{
                    return requestIp(host,useDefault).map(List::of);
                }
            }
            addrType=AddrType.IPV4;
        }
        var recordType = switch (addrType){
            case IPV4 -> DnsRecordType.A;
            case IPV6 -> DnsRecordType.AAAA;
        };

        if (resolveAll){
            return requestAll(host,recordType,useDefault);
        }else{
            return requestIp(host,recordType,useDefault).map(List::of);
        }
    }

    @Override
    public Future<List<InetAddress>> bootResolveAll(String host, AddrType addrType) {
        return doResolve(host,addrType,true,true);
    }
    @Override
    public Future<InetAddress> bootResolve(String host,AddrType addrType) {
        return doResolve(host,addrType,true,false).map(List::getFirst);
    }

    @Override
    public Future<List<InetAddress>> resolveAll(String host, AddrType addrType) {
        return doResolve(host,addrType,false,true);
    }
    @Override
    public Future<InetAddress> resolve(String host, AddrType addrType) {
        return doResolve(host,addrType,false,false).map(List::getFirst);
    }
}
