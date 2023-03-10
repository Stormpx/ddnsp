package io.crowds.dns;

import io.crowds.proxy.NetAddr;
import io.crowds.util.Inet;
import io.netty.buffer.*;
import io.netty.handler.codec.dns.*;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.SocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;


public class DohUpstream implements DnsUpstream {
    private final static Logger logger= LoggerFactory.getLogger(DohUpstream.class);
    private final static String CONTENT_TYPE="application/dns-message";
    private InternalDnsResolver resolver;
    private URL target;
    private java.net.InetSocketAddress remoteAddr;
    private HttpClient httpClient;

    private DnsRecordEncoder encoder=DnsRecordEncoder.DEFAULT;
    private DnsRecordDecoder decoder=DnsRecordDecoder.DEFAULT;

    public DohUpstream(Vertx vertx,URI target,InternalDnsResolver resolver) {
        this.resolver=resolver;
        try {
            this.target=target.toURL();
            this.remoteAddr = Inet.createSocketAddress(
                    this.target.getHost(),
                    this.target.getPort()!=-1 ? this.target.getPort() : this.target.getDefaultPort()
            );
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        this.httpClient = vertx.createHttpClient(new HttpClientOptions()
                .setDefaultHost(target.getHost())
                .setShared(true)
                .setUseAlpn(true)
                .setProtocolVersion(HttpVersion.HTTP_2)
        );
    }

    private Future<SocketAddress> getServerAddress(){
        if (!remoteAddr.isUnresolved()){
            return Future.succeededFuture(SocketAddress.inetSocketAddress(remoteAddr));
        }
        return resolver.bootResolve(target.getHost(),null)
                .map(addr->new InetSocketAddress(addr, target.getPort()!=-1 ? target.getPort() : target.getDefaultPort()))
                .map(SocketAddress::inetSocketAddress);
    }

    private ByteBuf encodeQuery(DnsQuery query, ByteBufAllocator allocator) throws Exception {
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


    private DnsResponse decodeResponse(ByteBuf buf) throws Exception {
        int id = buf.readUnsignedShort();
        int flags = buf.readUnsignedShort();
        if (flags>>15 == 0){
            throw new RuntimeException("body is not valid dns response");
        }
        var response = new SafeDnsResponse(id,
                DnsOpCode.valueOf((flags>>11) & 0xf),
                DnsResponseCode.valueOf(flags & 0xf)
        )
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
        return getServerAddress()
                .compose(server->{
                    try {
                        var buf = encodeQuery(query, UnpooledByteBufAllocator.DEFAULT);
                        return httpClient.request(new RequestOptions()
                                        .setServer(server)
                                        .setURI(target.getPath())
                                        .setSsl(target.getProtocol().equals("https"))
                                        .setMethod(HttpMethod.POST)
                                        .addHeader("accept",CONTENT_TYPE)
                                        .addHeader("content-type",CONTENT_TYPE)
                                        .setTimeout(5000)
                                        .setFollowRedirects(true)
                                )
                                .compose(req->req.send(Buffer.buffer(buf)))
                                .compose(resp->{
                                    if (resp.statusCode()!=200){
                                        return Future.failedFuture("http status code = "+ resp.statusCode());
                                    }
                                    String contentType = resp.getHeader("content-type");
                                    if (!Objects.equals(contentType,CONTENT_TYPE)){
                                        return Future.failedFuture("unrecognized content type: "+ contentType);
                                    }
                                    return resp.body();
                                })
                                .compose(buffer->{
                                    try {
                                        return Future.succeededFuture(decodeResponse(buffer.getByteBuf()));
                                    } catch (Exception e) {
                                        return Future.failedFuture(e);
                                    }
                                });
                    } catch (Exception e) {
                        return Future.failedFuture(e);
                    }
                });
    }

}
