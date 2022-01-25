package io.crowds.proxy.select;

import io.crowds.proxy.ProxyContext;

import java.time.Clock;
import java.time.LocalTime;
import java.util.List;

public class Time extends TransportSelector{


    public Time(String name) {
        super(name);
    }

    @Override
    public List<String> tags() {
        return null;
    }

    @Override
    public String nextTag(ProxyContext proxyContext) {
        return null;
    }

    
}
