package io.crowds.ddns;

import io.vertx.core.Future;

public interface IpProvider {


    Future<String> getIpv4();

    default Future<String> getIpv6(){
        return Future.failedFuture(new UnsupportedOperationException("ipv6 is not supported"));
    }
}
