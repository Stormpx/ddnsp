package io.crowds.proxy;

import io.crowds.proxy.dns.FakeOption;
import io.crowds.proxy.services.socks.SocksOption;
import io.crowds.proxy.services.transparent.TransparentOption;
import io.crowds.proxy.transport.ProtocolOption;

import java.util.List;

public class ProxyOption {

    private SocksOption socks;

    private TransparentOption transparent;

    private List<ProtocolOption> proxies;

    private List<String> rules;

    private FakeOption fakeDns;

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

    public List<String> getRules() {
        return rules;
    }

    public ProxyOption setRules(List<String> rules) {
        this.rules = rules;
        return this;
    }

    public FakeOption getFakeDns() {
        return fakeDns;
    }

    public ProxyOption setFakeDns(FakeOption fakeDns) {
        this.fakeDns = fakeDns;
        return this;
    }
}
