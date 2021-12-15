import io.crowds.proxy.*;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.shadowsocks.Cipher;
import io.crowds.proxy.transport.shadowsocks.ShadowsocksOption;
import io.crowds.proxy.transport.shadowsocks.ShadowsocksTransport;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.dns.*;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.Future;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class ShadowsocksTest {

    private EndPoint createEndPoint(NetLocation location) throws InterruptedException {
        NioEventLoopGroup executors = new NioEventLoopGroup();
        ChannelCreator creator = new ChannelCreator(executors);
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16827);
        var option=new ShadowsocksOption()
                .setAddress(dest)
                .setCipher(Cipher.CHACHA20_IETF_POLY1305)
                .setPassword("passpasspass");
        option.setConnIdle(5);
        option.setName("ss");
        ShadowsocksTransport transport = new ShadowsocksTransport(executors, creator, option);
        var context=new ProxyContext(executors.next(),location);
        Future<EndPoint> future = transport.createEndPoint(context);
        future.sync();

        assert future.isSuccess();

        return future.getNow();
    }

    @Test
    public void tcpTest() throws InterruptedException {

        NetLocation location = new NetLocation(null, new DomainNetAddr("www.baidu.com", 80), TP.TCP);
        EndPoint endPoint = createEndPoint(location);

        endPoint.bufferHandler(buf->{
            System.out.println(buf.toString(StandardCharsets.UTF_8));
        });

        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestEncoder());
        var header=new DefaultHttpHeaders()
                .add(HttpHeaderNames.HOST,"www.baidu.com")
                .add(HttpHeaderNames.ACCEPT,"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .add(HttpHeaderNames.CONNECTION,"close");

        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/",header);

        channel.writeOutbound(request);

        ByteBuf b=channel.readOutbound();
        System.out.println(b.toString(StandardCharsets.UTF_8));

        endPoint.write(b);

        endPoint.closeFuture().addListener(f -> {
            System.out.println("closed");
        }).sync();



    }

    @Test
    public void udpTest() throws Exception {

        EmbeddedChannel channel = new EmbeddedChannel(new DatagramDnsQueryEncoder(),new DatagramDnsResponseDecoder());
        InetSocketAddress address = new InetSocketAddress("114.114.114.114", 53);
        NetLocation location = new NetLocation(new NetAddr(InetSocketAddress.createUnresolved("127.0.0.1",64423)),new NetAddr(address), TP.UDP);
        EndPoint endPoint = createEndPoint(location);
        endPoint.bufferHandler(buf->{
            try {
                channel.writeInbound(new DatagramPacket(buf,null,address));
                DnsResponse response=channel.readInbound();

                System.out.println(response);
            } catch (Exception e) {
                e.printStackTrace();
            }

        });



        DatagramDnsQuery query = new DatagramDnsQuery(null, address,0, DnsOpCode.QUERY);
        query.setRecursionDesired(true);
        query.addRecord(DnsSection.QUESTION,new DefaultDnsQuestion("www.google.com.",DnsRecordType.A));
        System.out.println(query);
        channel.writeOutbound(query);
        DatagramPacket packet=channel.readOutbound();
        endPoint.write(packet.content());

        System.out.println();

        query = new DatagramDnsQuery(null, address,1, DnsOpCode.QUERY);
        query.setRecursionDesired(true);
        query.addRecord(DnsSection.QUESTION,new DefaultDnsQuestion("www.bilibili.com.",DnsRecordType.A));
        System.out.println(query);
        channel.writeOutbound(query);
        packet=channel.readOutbound();
        endPoint.write(packet.content());

        endPoint.closeFuture().sync();

    }
}