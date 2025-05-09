package io.crowds.compoments.dns;

import io.crowds.util.AddrType;
import io.vertx.core.Future;

import java.net.InetAddress;
import java.util.List;

public interface InternalDnsResolver {
    /**
     * using fallback udp dns server
     * which 8.8.8.8
     * @param host the specified hostname
     * @return
     */
    Future<List<InetAddress>> bootResolveAll(String host,AddrType addrType);

    Future<InetAddress> bootResolve(String host,AddrType addrType);

    Future<List<InetAddress>> resolveAll(String host, AddrType addrType);

    Future<InetAddress> resolve(String host, AddrType addrType);

}
