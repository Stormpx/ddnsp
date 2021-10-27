package io.crowds.proxy;

import io.crowds.proxy.services.socks.SocksOption;
import io.crowds.proxy.services.transparent.TransparentOption;
import io.crowds.proxy.transport.ProtocolOption;

import java.util.List;

public class ProxyOption {

    private SocksOption socks;

    private TransparentOption transparent;

    private List<ProtocolOption> proxies;


    public SocksOption getSocks() {
        return socks;
    }

    public ProxyOption setSocks(SocksOption socks) {
        this.socks = socks;
        return this;
    }

    public TransparentOption getTransparent() {
        return transparent;
    }

    public ProxyOption setTransparent(TransparentOption transparent) {
        this.transparent = transparent;
        return this;
    }

    public List<ProtocolOption> getProxies() {
        return proxies;
    }

    public ProxyOption setProxies(List<ProtocolOption> proxies) {
        this.proxies = proxies;
        return this;
    }
}
