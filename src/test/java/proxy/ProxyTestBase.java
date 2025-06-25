package proxy;

import io.crowds.Context;
import io.crowds.Ddnsp;
import io.crowds.proxy.*;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.util.Inet;
import io.netty.buffer.ByteBuf;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.dns.*;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.Future;
import io.vertx.core.internal.VertxInternal;
import org.junit.Rule;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public abstract class ProxyTestBase {
    public static final Network CONTAINER_NETWORK = Network.newNetwork();

    protected Context context = Ddnsp.newContext(Ddnsp::dnsResolver);
    protected VertxInternal vertx = context.getVertx();
    protected EventLoopGroup eventLoopGroup=vertx.nettyEventLoopGroup();
    protected ChannelCreator channelCreator=new ChannelCreator(context);


    @Rule
    public GenericContainer<?> nginx = new GenericContainer<>("nginx")
            .withNetwork(CONTAINER_NETWORK)
            .withNetworkAliases("nginx")
            .withExposedPorts(80);


    public EndPoint createEndPoint(ProxyTransport proxyTransport, NetLocation location) throws Exception {

        var context=new ProxyContext(eventLoopGroup.next(),location);
        Future<EndPoint> future = proxyTransport.createEndPoint(context);
        future.sync();

        assert future.isSuccess();

        EndPoint endPoint = future.getNow();
        endPoint.setAutoRead(true);
        return endPoint;
    }

    private void readEndpointResponse(CountDownLatch latch, EndPoint endPoint,EmbeddedChannel channel){
        endPoint.bufferHandler(buf->{
            System.out.println(((ByteBuf)buf).toString(StandardCharsets.UTF_8));
            channel.writeInbound(buf);
            Object read;
            while ((read=channel.readInbound())!=null){
                if (read instanceof  LastHttpContent){
                    latch.countDown();
                }
            }
        });
    }

    public void tcpTest(ProxyTransport proxyTransport,NetAddr targetAddress) throws Exception {

        NetLocation location = new NetLocation(new NetAddr(new InetSocketAddress("127.0.0.1",0)),
                targetAddress, TP.TCP);
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestEncoder(),new HttpResponseDecoder());
        var header=new DefaultHttpHeaders()
                .add(HttpHeaderNames.HOST,"test.ddnsp.org")
                .add(HttpHeaderNames.ACCEPT,"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .add(HttpHeaderNames.CONNECTION,"close");

        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/",header);

        channel.writeOutbound(request);

        ByteBuf b=channel.readOutbound();
        System.out.println(b.toString(StandardCharsets.UTF_8));

        EndPoint endPoint = createEndPoint(proxyTransport,location);

        CountDownLatch countDownLatch=new CountDownLatch(1);
        readEndpointResponse(countDownLatch,endPoint,channel);

        endPoint.write(b);

        endPoint.closeFuture().addListener(f -> {
            System.out.println("endpoint closed");
        });

        if (!countDownLatch.await(3, TimeUnit.SECONDS)){
            throw new RuntimeException("timeout..");
        }
        endPoint.close();

        Thread.sleep(500);
    }

    public void tcpTest(ProxyTransport proxyTransport) throws Exception {
        tcpTest(proxyTransport,new DomainNetAddr("nginx", 80));
    }


    public void udpTest(ProxyTransport proxyTransport) throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new DatagramDnsQueryEncoder(),new DatagramDnsResponseDecoder());
        InetSocketAddress address = new InetSocketAddress("114.114.114.114", 53);
        NetLocation location = new NetLocation(new NetAddr(Inet.createSocketAddress("127.0.0.1",64423)),new NetAddr(address), TP.UDP);


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

        System.out.println();

        query = new DatagramDnsQuery(null, address,1, DnsOpCode.QUERY);
        query.setRecursionDesired(true);
        query.addRecord(DnsSection.QUESTION,new DefaultDnsQuestion("www.bilibili.com.",DnsRecordType.A));
        System.out.println(query);
        channel.writeOutbound(query);
        packet=channel.readOutbound();
        endPoint.write(packet);

        if (!countDownLatch.await(5,TimeUnit.SECONDS)){
            throw new RuntimeException("timeout..");
        }
        endPoint.close();

    }


}
