package io.crowds.proxy.select;

import io.crowds.proxy.ProxyTransport;

import java.util.List;

public record Transport(ProxyTransport proxy,List<String> chain) {

}
