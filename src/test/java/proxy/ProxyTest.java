package proxy;

import io.crowds.proxy.*;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.ProxyTransport;
import io.netty.buffer.ByteBuf;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.dns.*;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.Future;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public abstract class ProxyTest {

    protected EventLoopGroup eventLoopGroup=new NioEventLoopGroup();
    protected ChannelCreator channelCreator=new ChannelCreator(eventLoopGroup);


    public EndPoint createEndPoint(ProxyTransport proxyTransport, NetLocation location) throws Exception {

        var context=new ProxyContext(eventLoopGroup.next(),location);
        Future<EndPoint> future = proxyTransport.createEndPoint(context);
        future.sync();

        assert future.isSuccess();

        EndPoint endPoint = future.getNow();
        endPoint.setAutoRead(true);
        return endPoint;
    }

    public void tcpTest(ProxyTransport proxyTransport) throws Exception {
        NetLocation location = new NetLocation(null, new DomainNetAddr("www.ip.cn", 80), TP.TCP);
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestEncoder());
        var header=new DefaultHttpHeaders()
                .add(HttpHeaderNames.HOST,"www.ip.cn")
                .add(HttpHeaderNames.ACCEPT,"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .add(HttpHeaderNames.CONNECTION,"close");

        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/",header);

        channel.writeOutbound(request);

        ByteBuf b=channel.readOutbound();
        System.out.println(b.toString(StandardCharsets.UTF_8));

        EndPoint endPoint = createEndPoint(proxyTransport,location);

        CountDownLatch countDownLatch=new CountDownLatch(1);
        endPoint.bufferHandler(buf->{
            System.out.println(((ByteBuf)buf).toString(StandardCharsets.UTF_8));
            countDownLatch.countDown();
        });
        endPoint.write(b);

        endPoint.closeFuture().addListener(f -> {
            System.out.println("closed");
        });

        if (!countDownLatch.await(3, TimeUnit.SECONDS)){
            throw new RuntimeException("timeout..");
        }
        endPoint.close();

        Thread.sleep(1000);

    }


    public void udpTest(ProxyTransport proxyTransport) throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new DatagramDnsQueryEncoder(),new DatagramDnsResponseDecoder());
        InetSocketAddress address = new InetSocketAddress("114.114.114.114", 53);
        NetLocation location = new NetLocation(new NetAddr(InetSocketAddress.createUnresolved("127.0.0.1",64423)),new NetAddr(address), TP.UDP);


        DatagramDnsQuery query = new DatagramDnsQuery(null, address,0, DnsOpCode.QUERY);
        query.setRecursionDesired(true);
        query.addRecord(DnsSection.QUESTION,new DefaultDnsQuestion("www.google.com.",DnsRecordType.A));
        System.out.println(query);
        channel.writeOutbound(query);
        DatagramPacket packet=channel.readOutbound();
        EndPoint endPoint = createEndPoint(proxyTransport,location);
        CountDownLatch countDownLatch = new CountDownLatch(2);
        endPoint.bufferHandler(buf->{
            try {
                channel.writeInbound(buf);
                DnsResponse response=channel.readInbound();
                System.out.println(response);
                countDownLatch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
            }

        });
        endPoint.write(packet);

//        System.out.println();
//
//        query = new DatagramDnsQuery(null, address,1, DnsOpCode.QUERY);
//        query.setRecursionDesired(true);
//        query.addRecord(DnsSection.QUESTION,new DefaultDnsQuestion("www.bilibili.com.",DnsRecordType.A));
//        System.out.println(query);
//        channel.writeOutbound(query);
//        packet=channel.readOutbound();
//        endPoint.write(packet);

        if (!countDownLatch.await(5,TimeUnit.SECONDS)){
            throw new RuntimeException("timeout..");
        }
        endPoint.close();

    }


}
