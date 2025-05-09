package io.crowds.compoments.dns;

public class VariantResolver {

    private final InternalDnsResolver internalDnsResolver;
    private final DdnspAddressResolverGroup nettyResolverGroup;


    public VariantResolver(InternalDnsResolver internalDnsResolver) {
        this.internalDnsResolver = internalDnsResolver;
        this.nettyResolverGroup = new DdnspAddressResolverGroup(()->internalDnsResolver);
    }


    public InternalDnsResolver getInternalDnsResolver() {
        return internalDnsResolver;
    }

    public DdnspAddressResolverGroup getNettyResolver() {
        return nettyResolverGroup;
    }
}
