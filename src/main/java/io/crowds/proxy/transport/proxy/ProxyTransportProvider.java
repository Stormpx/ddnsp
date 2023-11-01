package io.crowds.proxy.transport.proxy;

import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.proxy.transport.proxy.AbstractProxyTransport;

public interface ProxyTransportProvider {


    ProxyTransport get(String name);

    ProxyTransport create(ProtocolOption protocolOption);
}
