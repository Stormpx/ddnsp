package io.crowds.ddns.resolve;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;

public interface DnsResolver {

    void setConfig(JsonObject config);

    Future<List<DomainRecord>> getRecord(String domainName);

    Future<Void> updateDnsResolve(String targetId,DomainRecord updateRecord);


}
