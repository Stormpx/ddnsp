package io.crowds.dns;

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
    Future<InetAddress> bootResolve(String host,StandardProtocolFamily targetFamily);

    Future<InetAddress> resolve(String host, StandardProtocolFamily targetFamily);

}
