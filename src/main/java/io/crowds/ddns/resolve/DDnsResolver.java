package io.crowds.ddns.resolve;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;

public interface DDnsResolver {

    void setConfig(JsonObject config);

    Future<List<DomainRecord>> getRecord(String domainName);

    Future<Void> updateDnsResolve(DomainRecord updateRecord);


}
