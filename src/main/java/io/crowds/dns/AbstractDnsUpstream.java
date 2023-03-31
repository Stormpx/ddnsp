package io.crowds.dns;

import io.vertx.core.Future;

import java.net.InetSocketAddress;

public abstract class AbstractDnsUpstream implements DnsUpstream{

    private InternalDnsResolver internalDnsResolver;

    public AbstractDnsUpstream(InternalDnsResolver internalDnsResolver) {
        this.internalDnsResolver = internalDnsResolver;
    }


    protected Future<InetSocketAddress> bootLookup(InetSocketAddress remote){
        if (!remote.isUnresolved()){
            return Future.succeededFuture(remote);
        }
        return internalDnsResolver.bootResolve(remote.getHostString(),null)
                .map(inetAddress -> new InetSocketAddress(inetAddress,remote.getPort()));
    }

}
