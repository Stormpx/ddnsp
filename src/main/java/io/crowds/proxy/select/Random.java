package io.crowds.proxy.select;

import io.crowds.proxy.ProxyContext;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Random extends TransportSelector{
    private List<String> tags;

    public Random(String name,List<String> tags) {
        super(name);
        Objects.requireNonNull(tags);
        assert !tags.isEmpty();
        this.tags = tags;
    }


    @Override
    public List<String> tags() {
        return tags;
    }

    @Override
    public String nextTag(ProxyContext proxyContext) {
        int index = ThreadLocalRandom.current().nextInt(0, tags.size());
        return tags.get(index);
    }


}

