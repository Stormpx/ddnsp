package io.crowds.ddns.resolve;

import io.netty.util.NetUtil;

import java.net.InetAddress;
import java.util.Objects;

public class DomainRecord {
    private String id;
    private String name;
    private int ttl;
    private String type;
    private String content;

    public boolean isIpv4Record(){
        return Objects.equals("A",type);
    }

    public boolean isIpv6Record(){
        return Objects.equals("AAAA",type);
    }

    public InetAddress getInetAddress(){
        return NetUtil.createInetAddressFromIpAddressString(content);
    }

    public String getId() {
        return id;
    }

    public DomainRecord setId(String id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public DomainRecord setName(String name) {
        this.name = name;
        return this;
    }

    public int getTtl() {
        return ttl;
    }

    public DomainRecord setTtl(int ttl) {
        this.ttl = ttl;
        return this;
    }

    public String getType() {
        return type;
    }

    public DomainRecord setType(String type) {
        this.type = type;
        return this;
    }

    public String getContent() {
        return content;
    }

    public DomainRecord setContent(String content) {
        this.content = content;
        return this;
    }

    @Override
    public String toString() {
        return "DomainRecord{" + "rId='" + id + '\'' + ", name='" + name + '\'' + ", ttl=" + ttl + ", type='" + type + '\'' + ", content='" + content + '\'' + '}';
    }
}
