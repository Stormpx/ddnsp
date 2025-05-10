package io.crowds.compoments.dns;

import java.util.function.Supplier;

public class VariantResolver {

    private final Supplier<InternalDnsResolver> internalDnsResolver;
    private final DdnspAddressResolverGroup nettyResolverGroup;


    public VariantResolver(Supplier<InternalDnsResolver> internalDnsResolver) {
        this.internalDnsResolver = internalDnsResolver;
        this.nettyResolverGroup = new DdnspAddressResolverGroup(internalDnsResolver);
    }


    public InternalDnsResolver getInternalDnsResolver() {
        return internalDnsResolver.get();
    }

    public DdnspAddressResolverGroup getNettyResolver() {
        return nettyResolverGroup;
    }
}
