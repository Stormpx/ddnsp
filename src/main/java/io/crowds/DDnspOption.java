package io.crowds;

import io.crowds.ddns.DDnsOption;
import io.crowds.dns.DnsOption;
import io.crowds.proxy.ProxyOption;

public class DDnspOption {

    private String logLevel;

    private String mmdb;

    private DnsOption dns;

    private DDnsOption ddns;

    private ProxyOption proxy;



    public DnsOption getDns() {
        return dns;
    }

    public DDnspOption setDns(DnsOption dns) {
        this.dns = dns;
        return this;
    }

    public DDnsOption getDdns() {
        return ddns;
    }

    public DDnspOption setDdns(DDnsOption ddns) {
        this.ddns = ddns;
        return this;
    }

    public ProxyOption getProxy() {
        return proxy;
    }

    public DDnspOption setProxy(ProxyOption proxy) {
        this.proxy = proxy;
        return this;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public DDnspOption setLogLevel(String logLevel) {
        this.logLevel = logLevel;
        return this;
    }

    public String getMmdb() {
        return mmdb;
    }

    public DDnspOption setMmdb(String mmdb) {
        this.mmdb = mmdb;
        return this;
    }
}
