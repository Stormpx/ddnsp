package io.crowds.dns;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;

public class DnsOption {
    private boolean enable=true;

    private List<URI> dnsServers;

    private boolean ipv6;
    private String host;
    private Integer port;
    private int ttl;


    private Map<String,RecordData> rrMap;

    public ClientOption genClientOption(){
        return new ClientOption()
                .setUpstreams(dnsServers)
                .setTryIpv6(ipv6);
    }

    public boolean isEnable() {
        return enable;
    }

    public DnsOption setEnable(boolean enable) {
        this.enable = enable;
        return this;
    }

    public String getHost() {
        return host;
    }

    public DnsOption setHost(String host) {
        this.host = host;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public DnsOption setPort(Integer port) {
        this.port = port;
        return this;
    }

    public List<URI> getDnsServers() {
        return dnsServers;
    }

    public DnsOption setDnsServers(List<URI> dnsServers) {
        this.dnsServers = dnsServers;
        return this;
    }

    public int getTtl() {
        return ttl;
    }

    public DnsOption setTtl(int ttl) {
        this.ttl = ttl;
        return this;
    }




    public Map<String, RecordData> getRrMap() {
        return rrMap;
    }

    public DnsOption setRrMap(Map<String, RecordData> rrMap) {
        this.rrMap = rrMap;
        return this;
    }

    public boolean isIpv6() {
        return ipv6;
    }

    public DnsOption setIpv6(boolean ipv6) {
        this.ipv6 = ipv6;
        return this;
    }
}
