package io.crowds.proxy.select;

import io.crowds.proxy.ProxyTransport;

import java.util.List;
import java.util.stream.Collectors;

public record Transport(ProxyTransport proxy,List<String> chain) {

    public String getChain(){
        if (chain==null)
            return "";
        return String.join("->", chain);
    }

}
