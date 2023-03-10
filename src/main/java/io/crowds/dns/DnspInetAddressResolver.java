package io.crowds.dns;

import io.crowds.Ddnsp;
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

public class DnspInetAddressResolver implements InetAddressResolver {

    private InetAddressResolver builtInResolver;

    public DnspInetAddressResolver(InetAddressResolver builtInResolver) {
        this.builtInResolver = builtInResolver;
    }

    private CompositeFuture lookupWithPolicy(DnsClient dnsClient,String host, LookupPolicy lookupPolicy){
        List<Future> r=new ArrayList<>(2);
        if ((lookupPolicy.characteristics()&LookupPolicy.IPV4_FIRST)!=0){
            r.add(dnsClient.request(host, DnsRecordType.A).map(it->DnsKit.getInetAddrFromResponse(it,true)));
            if ((lookupPolicy.characteristics()&LookupPolicy.IPV6)!=0){
                r.add(dnsClient.request(host, DnsRecordType.AAAA)
                        .map(it->DnsKit.getInetAddrFromResponse(it,false)));
            }
        }else{
            r.add(dnsClient.request(host, DnsRecordType.AAAA).map(it->DnsKit.getInetAddrFromResponse(it,false)));
            if ((lookupPolicy.characteristics()&LookupPolicy.IPV4)!=0){
                r.add(dnsClient.request(host, DnsRecordType.A).map(it->DnsKit.getInetAddrFromResponse(it,true)));
            }
        }

        return CompositeFuture.all(r);
    }




    @Override
    public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy) throws UnknownHostException {
        try {
            InternalDnsResolver resolver = Ddnsp.dnsResolver();
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
