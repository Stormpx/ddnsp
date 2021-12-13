package io.crowds.ddns;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.RequestOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class HttpIpProvider implements IpProvider {
    private final static Logger logger= LoggerFactory.getLogger(HttpIpProvider.class);


    private final static Pattern pattern = Pattern.compile("\\d+.\\d+.\\d+.\\d+");

    private final static List<String> apis=new ArrayList<>();
    static {
        apis.add("http://ip.3322.net/");
        apis.add("http://myip.ipip.net/");
        apis.add("https://ipv4.lookup.test-ipv6.com/ip/");
        apis.add("http://ip-api.com/json/?fields=query");
        apis.add("http://ipinfo.io/ip");
    }

    private HttpClient httpClient;

    private List<String> urls;

    public HttpIpProvider(HttpClient httpClient) {
        this(httpClient,apis);
    }

    public HttpIpProvider(HttpClient httpClient, List<String> urls) {
        Objects.requireNonNull(httpClient);
        Objects.requireNonNull(urls);
        if (urls.isEmpty()) {
            logger.warn("url list is empty. use default urls instead");
            urls = apis;
        }
        this.httpClient = httpClient;
        this.urls = urls;
    }

    @Override
    public Future<String> getCurIpv4() {

        return nextApi(urls.iterator());
    }

    public Future<String> nextApi(Iterator<String> iterator){
        if (!iterator.hasNext())
            return Future.failedFuture("all getIpv4 api failure");
        String url = iterator.next();
        return httpClient.request(new RequestOptions()
                .putHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:72.0) Gecko/20100101 Firefox/72.0")
                .setAbsoluteURI(url)
                .setFollowRedirects(true))
                .compose(HttpClientRequest::send)
                .compose(resp->{
                    if (resp.statusCode()==200){
                        return resp.body();
                    }
                    return Future.failedFuture("statuscode == "+resp.statusCode()+" "+resp.statusMessage());
                })
                .map(Buffer::toString)
                .compose(str->{
                    Matcher matcher = pattern.matcher(str);
                    return matcher.find()?Future.succeededFuture(matcher.group()):Future.failedFuture("ipv4 not found");
                })
                .onFailure(t->logger.info("url:{} failed cause:{}",url,t.getMessage()))
                .recover(t->nextApi(iterator))
                ;
    }
}
