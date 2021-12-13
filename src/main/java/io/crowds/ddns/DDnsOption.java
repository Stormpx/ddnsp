package io.crowds.ddns;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Objects;

public class DDnsOption {
    private boolean enable;

    private JsonArray ipProviders;

    private JsonArray domains;

    private JsonArray resolvers;



    public boolean isEnable() {
        return enable;
    }

    public DDnsOption setEnable(boolean enable) {
        this.enable = enable;
        return this;
    }


    public JsonArray getIpProviders() {
        return ipProviders;
    }

    public DDnsOption setIpProviders(JsonArray ipProviders) {
        this.ipProviders = ipProviders;
        return this;
    }

    public JsonArray getDomains() {
        return domains;
    }

    public DDnsOption setDomains(JsonArray domains) {
        this.domains = domains;
        return this;
    }

    public JsonArray getResolvers() {
        return resolvers;
    }

    public DDnsOption setResolvers(JsonArray resolvers) {
        this.resolvers = resolvers;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DDnsOption that = (DDnsOption) o;
        return enable == that.enable && Objects.equals(ipProviders, that.ipProviders) && Objects.equals(domains, that.domains) && Objects.equals(resolvers, that.resolvers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enable, ipProviders, domains, resolvers);
    }
}
