import io.crowds.dns.DnsKit;
import io.crowds.dns.DnsOption;
import io.netty.handler.codec.dns.*;
import io.netty.util.internal.StringUtil;
import io.vertx.core.Vertx;
import io.vertx.core.dns.DnsClient;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class DnsTest {


    public static void main(String[] args) throws InterruptedException, ExecutionException {
        Vertx vertx = Vertx.vertx();

//        DnsClient dnsClient = vertx.createDnsClient(53,"114.114.114.114");


//        dnsClient.resolveCNAME("oss.davco.cn")
//                .onFailure(Throwable::printStackTrace)
//                .onSuccess(str-> System.out.println("cname"+str));

//        dnsClient.resolveA("oss.davco.cn")
//                .onFailure(Throwable::printStackTrace)
//                .onSuccess(str-> System.out.println(str));
//        InetSocketAddress inetSocketAddress = new InetSocketAddress("127.0.0.1", 53);
//        InetSocketAddress inetSocketAddress = new InetSocketAddress("114.114.114.114", 53);
        var dc=new io.crowds.dns.DnsClient(vertx,new DnsOption()
                .setDnsServers(List.of(
                        URI.create("dns://114.114.114.114:53")
//                        URI.create("https://doh.pub/dns-query")
                )));
        dc.request("www.baidu.com", DnsRecordType.A)
                .onFailure(Throwable::printStackTrace)
                .onSuccess(message->{
                    System.out.println("question"+appendRecords(message,DnsSection.QUESTION));
                    System.out.println("ANSWER"+appendRecords(message,DnsSection.ANSWER));
                    System.out.println("AUTHORITY"+appendRecords(message,DnsSection.AUTHORITY));
                    System.out.println("ADDITIONAL"+appendRecords(message,DnsSection.ADDITIONAL));

                }).toCompletionStage().toCompletableFuture().get();

        dc.request("www.baidu.com")
            .onSuccess(System.out::println);

//
//        dc.request("oss.davco.cn", DnsRecordType.CNAME)
//                .onFailure(Throwable::printStackTrace)
//                .onSuccess(str-> System.out.println("dcC"+str));

        vertx.nettyEventLoopGroup().awaitTermination(6, TimeUnit.SECONDS);
        vertx.close();

    }


    private static String appendRecords( DnsMessage message, DnsSection section) {
        StringBuilder buf=new StringBuilder();
        final int count = message.count(section);
        for (int i = 0; i < count; i ++) {
            DnsRecord dnsRecord = message.<DnsRecord>recordAt(section, i);
            buf.append(StringUtil.NEWLINE)
                    .append(StringUtil.TAB)
                    .append(dnsRecord);
            if (dnsRecord.type() == DnsRecordType.CNAME && dnsRecord instanceof DnsRawRecord raw){
                buf.append(StringUtil.TAB).append(DnsKit.decodeDomainName(raw.content()));
            }
        }
        return buf.toString();
    }

}


