package io.crowds.proxy.transport.proxy;

import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.proxy.transport.proxy.AbstractProxyTransport;

public interface ProxyTransportProvider {


    default ProxyTransport get(String name){
        return get(name,false);
    }

    ProxyTransport get(String name,boolean copy);

    ProxyTransport create(ProtocolOption protocolOption);

}
