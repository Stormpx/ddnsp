package dns.upstream;

import io.crowds.dns.*;
import io.netty.handler.codec.dns.*;
import io.netty.util.NetUtil;
import io.netty.util.internal.StringUtil;
import io.vertx.core.Vertx;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.TimeUnit;

public class Doh {
    public static void main(String[] args) throws InterruptedException {
        Vertx vertx = Vertx.vertx();

        try {
            var client = new DnsClient(vertx, new ClientOption());
//            var upstream = new DohUpstream(vertx, URI.create("https://doh.pub/dns-query"), client);
            var upstream = new DohUpstream(vertx, URI.create("https://dns.alidns.com/dns-query"), client);
            vertx.periodicStream(500)
                    .handler(id->{
                        upstream.lookup(
                                        new DefaultDnsQuery(-1, DnsOpCode.QUERY)
                                                .setRecursionDesired(true)
                                                .addRecord(DnsSection.QUESTION,new DefaultDnsQuestion("sf6-cdn-tos.douyinstatic.com",DnsRecordType.A, DnsRecord.CLASS_IN)))
                                .onSuccess(message->{
                                    System.out.println("question"+appendRecords(message,DnsSection.QUESTION));
                                    System.out.println("ANSWER"+appendRecords(message,DnsSection.ANSWER));
                                    System.out.println("AUTHORITY"+appendRecords(message,DnsSection.AUTHORITY));
                                    System.out.println("ADDITIONAL"+appendRecords(message,DnsSection.ADDITIONAL));
                                })
                                .onFailure(Throwable::printStackTrace);
                    });


            vertx.nettyEventLoopGroup().awaitTermination(10, TimeUnit.SECONDS);
        } finally {
            vertx.close();
        }


    }

    private static String appendRecords( DnsMessage message, DnsSection section) {
        StringBuilder buf=new StringBuilder();
        final int count = message.count(section);
        for (int i = 0; i < count; i ++) {
            buf.append(StringUtil.NEWLINE)
                    .append(StringUtil.TAB)
                    .append(message.<DnsRecord>recordAt(section, i));
        }
        return buf.toString();
    }
}
