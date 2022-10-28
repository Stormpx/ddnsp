package io.crowds.dns;

import io.crowds.util.DnsKit;
import io.netty.buffer.*;
import io.netty.handler.codec.dns.*;
import io.netty.util.CharsetUtil;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;


public class DohUpstream implements DnsUpstream {
    private final static Logger logger= LoggerFactory.getLogger(DohUpstream.class);
    private final static String CONTENT_TYPE="application/dns-message";
    private Vertx vertx;
    private URL target;
    private HttpClient httpClient;

    private DnsRecordEncoder encoder=DnsRecordEncoder.DEFAULT;
    private DnsRecordDecoder decoder=DnsRecordDecoder.DEFAULT;

    public DohUpstream(Vertx vertx,URI target) {
        this.vertx = vertx;
        try {
            this.target=target.toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        this.httpClient = vertx.createHttpClient(new HttpClientOptions()
                .setShared(true)
                .setUseAlpn(true)
                .setProtocolVersion(HttpVersion.HTTP_2)
        );
    }

    private String getUrl(){
        String url = target.toString();
        int index = url.indexOf("?");
        if (index!=-1){
            url=url.substring(0,index);
        }
        return url;
    }

    private ByteBuf encode(DnsQuery query, ByteBufAllocator allocator) throws Exception {
        ByteBuf buf = allocator.buffer();
        query.setId(0);
        DnsKit.encodeQueryHeader(query,buf);
        int questionCount = query.count(DnsSection.QUESTION);
        for (int i = 0; i < questionCount; i++) {
            encoder.encodeQuestion(query.recordAt(DnsSection.QUESTION,i), buf);
        }
        int additionalCount = query.count(DnsSection.ADDITIONAL);
        for (int i = 0; i < additionalCount; i++) {
            encoder.encodeRecord(query.recordAt(DnsSection.ADDITIONAL,i), buf);
        }
        return buf;
    }


    private DnsResponse decode(ByteBuf buf) throws Exception {
        int id = buf.readUnsignedShort();
        int flags = buf.readUnsignedShort();
        if (flags>>15 == 0){
            throw new RuntimeException("body is not valid dns response");
        }
        var response = new DefaultDnsResponse(id,
                DnsOpCode.valueOf((flags>>11) & 0xf),
                DnsResponseCode.valueOf(flags & 0xf))
                .setZ((flags>>4) & 0x7)
                .setAuthoritativeAnswer((flags & 1<<10 )!=0)
                .setRecursionDesired((flags & 1<<8 )!=0)
                .setRecursionAvailable((flags & 1<<7 )!=0);

        int questionCount = buf.readUnsignedShort();
        int answerCount = buf.readUnsignedShort();
        int authorityRecordCount = buf.readUnsignedShort();
        int additionalRecordCount = buf.readUnsignedShort();
        decodeRecord(response,buf,DnsSection.QUESTION,questionCount);
        decodeRecord(response,buf,DnsSection.ANSWER,answerCount);
        decodeRecord(response,buf,DnsSection.AUTHORITY,authorityRecordCount);
        decodeRecord(response,buf,DnsSection.ADDITIONAL,additionalRecordCount);

        return response;
    }

    private void decodeRecord(DnsResponse response, ByteBuf buf, DnsSection section, int count) throws Exception {
        for (int i = count; i > 0; i --) {
            response.addRecord(section, DnsSection.QUESTION==section?decoder.decodeQuestion(buf):decoder.decodeRecord(buf));
        }
    }



    @Override
    public Future<DnsResponse> lookup(DnsQuery query) {
        try {
            var buf = encode(query, UnpooledByteBufAllocator.DEFAULT);
            var base64= StandardCharsets.UTF_8.decode(Base64.getUrlEncoder().withoutPadding().encode(buf.nioBuffer()));
            buf.release();
            return httpClient.request(new RequestOptions()
                    .setAbsoluteURI(getUrl()+"?dns="+base64)
                    .setMethod(HttpMethod.GET)
                    .addHeader("accept",CONTENT_TYPE)
                    .setTimeout(5000)
                    .setFollowRedirects(true)
            )
                    .compose(HttpClientRequest::send)
                    .compose(resp->{
                        if (resp.statusCode()!=200){
                            return Future.failedFuture("http status code = "+ resp.statusCode());
                        }
                        String contentType = resp.getHeader("content-type");
                        if (!Objects.equals(contentType,CONTENT_TYPE)){
                            return Future.failedFuture("unsupported content type: "+ contentType);
                        }
                        return resp.body();
                    })
                    .compose(buffer->{
                        try {
                            return Future.succeededFuture(decode(buffer.getByteBuf()));
                        } catch (Exception e) {
                            return Future.failedFuture(e);
                        }
                    });
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
    }

}
