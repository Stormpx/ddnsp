package io.crowds.dns;

import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;

public class DnspInetAddressResolverProvider extends InetAddressResolverProvider {


    @Override
    public InetAddressResolver get(Configuration configuration) {
        return new DnspInetAddressResolver(configuration.builtinResolver());
    }

    @Override
    public String name() {
        return "ddnsp-resolver";
    }
}
