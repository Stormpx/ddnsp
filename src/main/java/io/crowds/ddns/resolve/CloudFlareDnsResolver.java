package io.crowds.ddns.resolve;

import io.crowds.util.Http;
import io.crowds.util.Strs;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CloudFlareDnsResolver  implements DnsResolver{
    private final static String DNS_RECORDS_LIST_URL="https://api.cloudflare.com/client/v4/zones/${zoneId}/dns_records";
    private final static String DNS_RECORDS_UPDATE_URL="https://api.cloudflare.com/client/v4/zones/${zoneId}/dns_records/${id}";
    private final static String DNS_RECORDS_CREATE_URL="https://api.cloudflare.com/client/v4/zones/${zoneId}/dns_records";

    private HttpClient httpClient;
    private JsonObject config;

    public CloudFlareDnsResolver(HttpClient httpClient, JsonObject config) {
        this.httpClient = httpClient;
        setConfig(config);
    }

    public void setConfig(JsonObject config) {
        if (config==null)
            throw new NullPointerException("cf config is null");
        this.config = config;
    }

    public Future<Object> checkSuccess(JsonObject json){
        if (json.getBoolean("success",false)){

            return Future.succeededFuture(json.getValue("result"));
        }else{
            return Future.failedFuture(json.getJsonArray("errors").encode());
        }
    }

    @Override
    public Future<List<DomainRecord>> getRecord(String domainName) {
        String zoneId = config.getString("zoneId");
        String apiToken = config.getString("apiToken");
        if (zoneId==null)
            return Future.failedFuture(new NullPointerException("zoneId"));
        if (apiToken==null){
            return Future.failedFuture(new NullPointerException("apiToken"));
        }
        String url = Strs.template(DNS_RECORDS_LIST_URL, Map.of("zoneId", zoneId));
        if (domainName!=null){
            url=url+"?name="+domainName;
        }
        return httpClient.request(new RequestOptions()
                    .setTimeout(1000*60)
                    .setFollowRedirects(true)
                    .putHeader("Authorization","Bearer "+apiToken)
                    .setAbsoluteURI(url))
                .compose(HttpClientRequest::send)
                .compose(Http::assertSuccess)
                .map(Buffer::toJsonObject)
                .compose(this::checkSuccess)
                .map(jsonArr->{
                    return ((JsonArray)jsonArr).stream()
                            .map(it->(JsonObject)it)
                            .map(json-> new DomainRecord()
                                    .setId(json.getString("id"))
                                    .setName(json.getString("name"))
                                    .setContent(json.getString("content"))
                                    .setType(json.getString("type"))
                                    .setTtl(json.getInteger("ttl")))
                            .collect(Collectors.toList())
                    ;
                });
    }

    @Override
    public Future<Void> updateDnsResolve( DomainRecord updateRecord) {
        if (updateRecord==null||updateRecord.getContent()==null){
            return Future.failedFuture(new NullPointerException("updateRecord"));
        }
        String zoneId = config.getString("zoneId");
        String apiToken = config.getString("apiToken");
        String targetId = updateRecord.getId();
        if (zoneId==null)
            return Future.failedFuture(new NullPointerException("zoneId"));
        if (apiToken==null){
            return Future.failedFuture(new NullPointerException("apiToken"));
        }

        String uri=targetId==null?
                Strs.template(DNS_RECORDS_CREATE_URL,Map.of("zoneId", zoneId)) :
                Strs.template(DNS_RECORDS_UPDATE_URL,Map.of("zoneId", zoneId,"id",targetId));
        HttpMethod method=targetId==null?HttpMethod.POST:HttpMethod.PUT;

        return httpClient
                .request(new RequestOptions()
                            .setAbsoluteURI(uri)
                            .setMethod(method)
                            .setTimeout(1000*60)
                            .setFollowRedirects(true)
                            .putHeader("content-type", HttpHeaderValues.APPLICATION_JSON)
                            .putHeader("Authorization","Bearer "+apiToken)
                        )
                .compose(req-> req.send(toJson(updateRecord).toBuffer()))
                .compose(Http::assertSuccess)
                .map(Buffer::toJsonObject)
                .compose(this::checkSuccess)
                .map((Void)null);

    }

    public JsonObject toJson(DomainRecord record){
        return new JsonObject()
                .put("type",record.getType())
                .put("ttl",record.getTtl())
                .put("content",record.getContent())
                .put("name",record.getName());
    }
}
