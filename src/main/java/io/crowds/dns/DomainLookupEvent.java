package io.crowds.dns;

import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.vertx.core.AsyncResult;
import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;

@Label("Internal Domain Lookup")
@Description("DnsClient lookup event")
@Category({"ddnsp","dns","lookup"})
public class DomainLookupEvent extends Event {
    @Label("Domain")
    private String domain;
    @Label("Type")
    private String type;
    @Label("Boot Lookup")
    private boolean boot;
    @Label("Hit Cache")
    private boolean hitCache;
    @Label("Result")
    private String result;

    public DomainLookupEvent(String domain, String type, boolean boot) {
        this.domain = domain;
        this.type = type;
        this.boot = boot;
    }

    public void commit(AsyncResult<DnsResponse> ar){
        if (!ar.succeeded()){
            setResult(ar.cause().getMessage());
        }else{
            DnsResponse response = ar.result();
            setResult(response.code().toString());
        }
        commit();
    }

    public void hitCacheCommit(){
        setHitCache(true)
                .setResult(DnsResponseCode.NOERROR.toString())
                .commit();
    }

    public String getDomain() {
        return domain;
    }

    public DomainLookupEvent setDomain(String domain) {
        this.domain = domain;
        return this;
    }

    public String getType() {
        return type;
    }

    public DomainLookupEvent setType(String type) {
        this.type = type;
        return this;
    }

    public boolean isBoot() {
        return boot;
    }

    public DomainLookupEvent setBoot(boolean boot) {
        this.boot = boot;
        return this;
    }

    public String getResult() {
        return result;
    }

    public DomainLookupEvent setResult(String result) {
        this.result = result;
        return this;
    }

    public boolean isHitCache() {
        return hitCache;
    }

    public DomainLookupEvent setHitCache(boolean hitCache) {
        this.hitCache = hitCache;
        return this;
    }
}
