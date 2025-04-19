package io.crowds.compoments.dns;

import io.crowds.Ddnsp;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.concurrent.EventExecutor;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.function.Supplier;

public class DdnspAddressResolverGroup  extends AddressResolverGroup<InetSocketAddress> {

    private final Supplier<InternalDnsResolver> resolverSupplier;

    public DdnspAddressResolverGroup(Supplier<InternalDnsResolver> resolverSupplier) {
        Objects.requireNonNull(resolverSupplier);
        this.resolverSupplier = resolverSupplier;
    }

    @Override
    protected io.netty.resolver.AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) throws Exception {
        return new DdnspNameResolver(executor, resolverSupplier.get()).asAddressResolver();
    }
}
