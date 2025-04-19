package io.crowds.compoments.dns;

import io.crowds.util.AddrType;
import io.vertx.core.Future;

import java.net.InetAddress;

public interface InternalDnsResolver {
    /**
     * using fallback udp dns server
     * which 8.8.8.8
     * @param host the specified hostname
     * @return
     */
    Future<InetAddress> bootResolve(String host,AddrType addrType);

    Future<InetAddress> resolve(String host, AddrType addrType);

}
