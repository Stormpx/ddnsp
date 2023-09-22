package io.crowds.ddns;

import io.vertx.core.Future;

import java.util.Objects;

public class StaticIpProvider implements IpProvider{

    private String ipv4;
    private String ipv6;

    public StaticIpProvider(String ipv4, String ipv6) {
        this.ipv4 = ipv4;
        this.ipv6 = ipv6;
    }

    @Override
    public Future<String> getIpv4() {
        return Future.succeededFuture(ipv4);
    }
}
