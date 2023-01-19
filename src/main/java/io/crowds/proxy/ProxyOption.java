package io.crowds.proxy;

import io.crowds.proxy.dns.FakeOption;
import io.crowds.proxy.services.http.HttpOption;
import io.crowds.proxy.services.socks.SocksOption;
import io.crowds.proxy.services.transparent.TransparentOption;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.tun.TunOption;
import io.vertx.core.json.JsonArray;

import java.util.List;

public class ProxyOption {

    private HttpOption http;

    private SocksOption socks;

    private TransparentOption transparent;

    private List<TunOption> tuns;

    private List<ProtocolOption> proxies;

    private JsonArray selectors;

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

    public JsonArray getSelectors() {
        return selectors;
    }

    public ProxyOption setSelectors(JsonArray selectors) {
        this.selectors = selectors;
        return this;
    }

    public HttpOption getHttp() {
        return http;
    }

    public ProxyOption setHttp(HttpOption http) {
        this.http = http;
        return this;
    }

    public List<TunOption> getTuns() {
        return tuns;
    }

    public ProxyOption setTuns(List<TunOption> tuns) {
        this.tuns = tuns;
        return this;
    }
}
