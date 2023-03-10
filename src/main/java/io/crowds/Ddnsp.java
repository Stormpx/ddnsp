package io.crowds;

import io.crowds.dns.DnsClient;
import io.crowds.dns.InternalDnsResolver;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

import java.util.concurrent.atomic.AtomicReference;

public class Ddnsp {

    public final static Vertx VERTX =Vertx.vertx(new VertxOptions()
            .setPreferNativeTransport(true));
    private final static AtomicReference<InternalDnsResolver> INTERNAL_DNS_RESOLVER =new AtomicReference<>();

    public static void initDnsResolver(InternalDnsResolver dnsClient){
        INTERNAL_DNS_RESOLVER.set(dnsClient);
    }

    public static InternalDnsResolver dnsResolver(){
        InternalDnsResolver client = INTERNAL_DNS_RESOLVER.get();
        if (client==null){
            INTERNAL_DNS_RESOLVER.compareAndSet(null,new DnsClient(VERTX,null));
            client=INTERNAL_DNS_RESOLVER.get();
        }
        return client;
    }

}
