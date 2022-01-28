package io.crowds.ddns;

import io.vertx.core.Future;

import java.net.InetAddress;
import java.util.Objects;

public class StaticIpProvider implements IpProvider{

    private String content;

    public StaticIpProvider(String content) {
        Objects.requireNonNull(content);
        this.content = content;
    }

    @Override
    public Future<String> getCurIpv4() {
        return Future.succeededFuture(content);
    }
}
