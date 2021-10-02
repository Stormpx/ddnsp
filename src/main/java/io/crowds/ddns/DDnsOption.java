package io.crowds.ddns;

import io.vertx.core.json.JsonObject;

public class DDnsOption {
    private boolean enable;

    private int refreshInterval;

    private Integer ttl;

    private String domain;

    private String resolver;

    private JsonObject ali;

    private JsonObject cf;



    public int getRefreshInterval() {
        return refreshInterval;
    }

    public DDnsOption setRefreshInterval(int refreshInterval) {
        this.refreshInterval = refreshInterval;
        return this;
    }

    public String getResolver() {
        return resolver;
    }

    public DDnsOption setResolver(String resolver) {
        this.resolver = resolver;
        return this;
    }

    public JsonObject getAli() {
        return ali;
    }

    public DDnsOption setAli(JsonObject ali) {
        this.ali = ali;
        return this;
    }

    public JsonObject getCf() {
        return cf;
    }

    public DDnsOption setCf(JsonObject cf) {
        this.cf = cf;
        return this;
    }

    public boolean isEnable() {
        return enable;
    }

    public DDnsOption setEnable(boolean enable) {
        this.enable = enable;
        return this;
    }

    public String getDomain() {
        return domain;
    }

    public DDnsOption setDomain(String domain) {
        this.domain = domain;
        return this;
    }

    public Integer getTtl() {
        return ttl;
    }

    public DDnsOption setTtl(Integer ttl) {
        this.ttl = ttl;
        return this;
    }
}
