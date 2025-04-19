package io.crowds.compoments.dns;

import io.crowds.dns.DnsClient;
import io.netty.handler.codec.dns.DnsRecordType;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

public class DdnspInetAddressResolver implements InetAddressResolver {

    private final InetAddressResolver builtInResolver;
    private final InternalDnsResolver internalDnsResolver;

    public DdnspInetAddressResolver(InetAddressResolver builtInResolver, InternalDnsResolver internalDnsResolver) {
        this.builtInResolver = builtInResolver;
        this.internalDnsResolver = internalDnsResolver;
    }

    private CompositeFuture lookupWithPolicy(DnsClient dnsClient, String host, LookupPolicy lookupPolicy){
        List<Future<?>> r=new ArrayList<>(2);
        if ((lookupPolicy.characteristics()&LookupPolicy.IPV4_FIRST)!=0){
            r.add(dnsClient.requestAll(host, DnsRecordType.A,false).map(List::stream));
            if ((lookupPolicy.characteristics()&LookupPolicy.IPV6)!=0){
                r.add(dnsClient.requestAll(host, DnsRecordType.AAAA,false).map(List::stream));
            }
        }else{
            r.add(dnsClient.requestAll(host, DnsRecordType.AAAA,false).map(List::stream));
            if ((lookupPolicy.characteristics()&LookupPolicy.IPV4)!=0){
                r.add(dnsClient.requestAll(host, DnsRecordType.A,false).map(List::stream));
            }
        }
        return Future.all(r);
    }




    @Override
    public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy) throws UnknownHostException {
        try {
            InternalDnsResolver resolver = internalDnsResolver;
            if (resolver instanceof DnsClient client){
                return lookupWithPolicy(client,host,lookupPolicy)
                        .toCompletionStage()
                        .toCompletableFuture()
                        .get(5, TimeUnit.SECONDS)
                        .list().stream().flatMap(it -> (Stream<InetAddress>) it);
            }else{
                return builtInResolver.lookupByName(host,lookupPolicy);
            }

        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            return builtInResolver.lookupByName(host,lookupPolicy);
        }

    }

    @Override
    public String lookupByAddress(byte[] addr) throws UnknownHostException {
        return builtInResolver.lookupByAddress(addr);
    }
}
