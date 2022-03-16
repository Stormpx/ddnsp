package io.crowds.proxy.select;

import io.crowds.proxy.transport.ProxyTransport;

import java.util.List;

public record Transport(ProxyTransport proxy,List<String> chain) {

    public String getChain(){
        if (chain==null)
            return "";
        return String.join("->", chain);
    }

}
