import io.crowds.dns.DnsOption;
import io.netty.handler.codec.dns.DnsMessage;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.internal.StringUtil;
import io.vertx.core.Vertx;
import io.vertx.core.dns.DnsClient;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Objects;

public class DnsTest {


    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

//        DnsClient dnsClient = vertx.createDnsClient(53,"114.114.114.114");


//        dnsClient.resolveCNAME("oss.davco.cn")
//                .onFailure(Throwable::printStackTrace)
//                .onSuccess(str-> System.out.println("cname"+str));

//        dnsClient.resolveA("oss.davco.cn")
//                .onFailure(Throwable::printStackTrace)
//                .onSuccess(str-> System.out.println(str));
//        InetSocketAddress inetSocketAddress = new InetSocketAddress("127.0.0.1", 53);
        InetSocketAddress inetSocketAddress = new InetSocketAddress("114.114.114.114", 53);
        var dc=new io.crowds.dns.DnsClient(vertx.nettyEventLoopGroup(),new DnsOption()
                .setDnsServers(Arrays.asList(inetSocketAddress)));
        dc.request("www.baidu.com", DnsRecordType.AAAA)
                .onFailure(Throwable::printStackTrace)
                .onSuccess(message->{
                    System.out.println("question"+appendRecords(message,DnsSection.QUESTION));
                    System.out.println("ANSWER"+appendRecords(message,DnsSection.ANSWER));
                    System.out.println("AUTHORITY"+appendRecords(message,DnsSection.AUTHORITY));
                    System.out.println("ADDITIONAL"+appendRecords(message,DnsSection.ADDITIONAL));
                });

        dc.request("www.baidu.com")
            .onSuccess(System.out::println);

//
//        dc.request("oss.davco.cn", DnsRecordType.CNAME)
//                .onFailure(Throwable::printStackTrace)
//                .onSuccess(str-> System.out.println("dcC"+str));


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


