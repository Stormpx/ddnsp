package io.crowds.ddns.resolve;

public class DomainRecord {
    private String rId;
    private String name;
    private int ttl;
    private String type;
    private String content;



    public String getrId() {
        return rId;
    }

    public DomainRecord setrId(String rId) {
        this.rId = rId;
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
        return "DomainRecord{" + "rId='" + rId + '\'' + ", name='" + name + '\'' + ", ttl=" + ttl + ", type='" + type + '\'' + ", content='" + content + '\'' + '}';
    }
}
