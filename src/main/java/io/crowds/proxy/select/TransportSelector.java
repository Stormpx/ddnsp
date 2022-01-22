package io.crowds.proxy.select;

import io.crowds.proxy.ProxyContext;

import java.util.List;

public abstract class TransportSelector {
    protected String name;

    public TransportSelector(String name) {
        this.name = name;
    }

    public abstract List<String> tags();

    public abstract String nextTag(ProxyContext proxyContext);

    public String getName() {
        return name;
    }

}
