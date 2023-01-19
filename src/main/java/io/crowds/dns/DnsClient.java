package io.crowds.dns;


import io.crowds.util.Inet;
import io.crowds.util.Strs;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.dns.*;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class DnsClient {
    private final Logger logger= LoggerFactory.getLogger(DnsClient.class);
    private Vertx vertx;
    private EventLoopGroup eventLoopGroup;

    private DnsOption dnsOption;
    private List<DnsUpstream> upStreams;


    public DnsClient(Vertx vertx,  DnsOption dnsOption) {
        this.vertx=vertx;
        this.dnsOption = dnsOption;
        this.eventLoopGroup= vertx.nettyEventLoopGroup();
        newUpStreams(dnsOption);


    }

    private void newUpStreams(DnsOption dnsOption){
        this.upStreams=dnsOption.getDnsServers().stream()
                .map(uri->{
                    String scheme = uri.getScheme();
                    return switch (Strs.isBlank(scheme)?"dns":scheme) {
                        case "dns", "udp" -> new UdpUpstream(eventLoopGroup, new InetSocketAddress(uri.getHost(), uri.getPort()));
                        case "http", "https" -> new DohUpstream(vertx, uri);
                        default -> {
                            logger.error("unsupported dns server {}",uri);
                            yield null;
                        }
                    };
                })
                .collect(Collectors.toList());;
    }

    public DnsClient setDnsOption(DnsOption dnsOption) {
        this.dnsOption = dnsOption;
        return this;
    }


    private DnsResponse copyResp(DnsResponse response){
        if (response instanceof SafeDnsResponse){
            return response;
        }
        DefaultDnsResponse dnsResponse = new DefaultDnsResponse(response.id(), response.opCode(), response.code());
        DnsKit.msgCopy(response,dnsResponse,true);
        ReferenceCountUtil.safeRelease(response);
        return dnsResponse;
    }

    public Future<DnsResponse> request(DnsQuery dnsQuery){

        return CompositeFuture.any(
                this.upStreams
                        .stream()
                        .map(upStreams->upStreams.lookup(dnsQuery).map(this::copyResp))
                        .collect(Collectors.toList())
        ).compose(cf -> IntStream.range(0,cf.size())
                .filter(cf::succeeded)
                .mapToObj(cf::<DnsResponse>resultAt)
                .findFirst()
                .map(Future::succeededFuture)
                .orElseGet(()->Future.failedFuture("no available upstream."))
        );

    }


    public Future<DnsResponse> request(String target,DnsRecordType type){
        if (!target.endsWith("."))
            target+=".";
        return request(new DefaultDnsQuery(-1,DnsOpCode.QUERY)
                .setRecursionDesired(true)
                .addRecord(DnsSection.QUESTION,new DefaultDnsQuestion(target,type,DnsRecord.CLASS_IN)));

    }


    public Future<InetAddress> request(String target){
        List<Future> fs=Stream.of(DnsRecordType.A,DnsRecordType.AAAA)
                .map(type -> request(target, type)
                        .map(resp-> DnsKit.getInetAddrFromResponse(resp, DnsRecordType.A == type)
                                .filter(Objects::nonNull)
                                .findFirst()
                                .orElse(null))
                )
                .collect(Collectors.toList());
        return CompositeFuture.any(fs)
                .compose(cf-> IntStream.range(0,cf.size())
                    .filter(cf::succeeded)
                    .mapToObj(cf::<InetAddress>resultAt)
                    .findFirst()
                    .map(Future::succeededFuture)
                    .orElseGet(()->Future.failedFuture("no available upstream.")));

    }



}
