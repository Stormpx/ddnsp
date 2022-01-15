package io.crowds.proxy;

public interface TransportProvider {


    String getTag();


    ProxyTransport getTransport(ProxyContext proxyContext);
}
