package io.crowds.compoments.dns;

import io.crowds.Ddnsp;

import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;

public class DdnspInetAddressResolverProvider extends InetAddressResolverProvider {


    @Override
    public InetAddressResolver get(Configuration configuration) {
        return new DdnspInetAddressResolver(configuration.builtinResolver(), Ddnsp.dnsResolver());
    }

    @Override
    public String name() {
        return "ddnsp-resolver";
    }
}
