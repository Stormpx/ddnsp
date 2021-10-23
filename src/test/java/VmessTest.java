import io.crowds.proxy.*;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.vmess.Security;
import io.crowds.proxy.transport.vmess.User;
import io.crowds.proxy.transport.vmess.VmessEndPoint;
import io.crowds.proxy.transport.vmess.VmessOption;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.dns.*;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.Future;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class VmessTest {

    private EndPoint createEndPoint( NetLocation location) throws InterruptedException {
        NioEventLoopGroup executors = new NioEventLoopGroup();
        ChannelCreator creator = new ChannelCreator(executors);
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16823);
        var option=new VmessOption()
                .setConnIdle(5)
                .setAddress(dest)
                .setSecurity(Security.ChaCha20_Poly1305)
                .setUser(new User(UUID.fromString("b831381d-6324-4d53-ad4f-8cda48b30811"),0));


        VmessEndPoint endPoint = new VmessEndPoint(location, option, creator);

        Future<EndPoint> future = endPoint.init();

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

        endPoint.closeFuture().addListener(future -> {
            System.out.println("closed");
        }).sync();



    }

    @Test
    public void udpTest() throws InterruptedException {

        EmbeddedChannel channel = new EmbeddedChannel(new DatagramDnsQueryEncoder(),new DatagramDnsResponseDecoder());
        InetSocketAddress address = new InetSocketAddress("114.114.114.114", 53);
        NetLocation location = new NetLocation(null,new NetAddr(address), TP.UDP);
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



        DatagramDnsQuery query = new DatagramDnsQuery(null, address,1, DnsOpCode.QUERY);
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
