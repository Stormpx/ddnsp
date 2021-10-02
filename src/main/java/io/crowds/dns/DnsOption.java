package io.crowds.dns;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

public class DnsOption {
    private boolean enable=true;

    private List<InetSocketAddress> dnsServers;
    private String host;
    private Integer port;
    private int ttl;

    private Map<String,RR> recordsMap;

    private Map<String,RecordData> rrMap;

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

    public List<InetSocketAddress> getDnsServers() {
        return dnsServers;
    }

    public DnsOption setDnsServers(List<InetSocketAddress> dnsServers) {
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


    public Map<String, RR> getRecordsMap() {
        return recordsMap;
    }

    public DnsOption setRecordsMap(Map<String, RR> recordsMap) {
        this.recordsMap = recordsMap;
        return this;
    }

    public Map<String, RecordData> getRrMap() {
        return rrMap;
    }

    public DnsOption setRrMap(Map<String, RecordData> rrMap) {
        this.rrMap = rrMap;
        return this;
    }
}
