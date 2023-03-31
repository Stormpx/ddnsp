package io.crowds.dns;

import io.crowds.util.AddrType;
import io.vertx.core.Future;

import java.net.InetAddress;
import java.net.StandardProtocolFamily;

public interface InternalDnsResolver {
    /**
     * using fallback udp dns server
     * which 114.114.114.114 or 8.8.8.8
     * @param host the specified hostname
     * @return
     */
    Future<InetAddress> bootResolve(String host,AddrType addrType);

    Future<InetAddress> resolve(String host, AddrType addrType);

}
