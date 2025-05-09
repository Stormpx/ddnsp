package io.crowds.compoments.dns;

import io.crowds.dns.DnsClient;
import io.netty.resolver.DefaultNameResolver;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;

import java.net.InetAddress;
import java.util.List;

public class DdnspNameResolver extends DefaultNameResolver {

    private final InternalDnsResolver dnsResolver;

    public DdnspNameResolver(EventExecutor executor, InternalDnsResolver dnsResolver) {
        super(executor);
        this.dnsResolver = dnsResolver;
    }

    @Override
    protected void doResolve(String inetHost, Promise<InetAddress> promise) throws Exception {
        dnsResolver.resolve(inetHost,null)
                     .onComplete(ar->{
                         if (ar.succeeded()){
                             promise.trySuccess(ar.result());
                         }else{
                             promise.tryFailure(ar.cause());
                         }
                     });
    }

    @Override
    protected void doResolveAll(String inetHost, Promise<List<InetAddress>> promise) throws Exception {
        InternalDnsResolver resolver = dnsResolver;
        var future = resolver.resolveAll(inetHost,null);
        future.onComplete(ar->{
            if (ar.succeeded()){
                promise.trySuccess(ar.result());
            }else{
                promise.tryFailure(ar.cause());
            }
        });
    }
}
