package proxy;

import io.crowds.Context;
import io.crowds.Ddnsp;
import io.crowds.proxy.*;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.util.Inet;
import io.crowds.util.Rands;
import io.crowds.util.Strs;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.dns.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.vertx.core.internal.VertxInternal;
import org.junit.Assert;
import org.junit.Rule;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.nginx.NginxContainer;
import org.testcontainers.utility.MountableFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public abstract class ProxyTestBase {

    private static final byte[] TEST_CONTENT = Rands.genBytes(1024 * 1024);

    public final Network CONTAINER_NETWORK = Network.newNetwork();

    protected Context context = Ddnsp.newContext(Ddnsp::dnsResolver);
    protected VertxInternal vertx = context.getVertx();
    protected EventLoopGroup eventLoopGroup=vertx.nettyEventLoopGroup();
    protected ChannelCreator channelCreator=new ChannelCreator(context);


//    @Rule
    public NginxContainer nginx = new NginxContainer("nginx")
            .withNetwork(CONTAINER_NETWORK)
            .withNetworkAliases("nginx")
            .withExposedPorts(80,443);
    {
        try {
            X509Bundle x509Bundle = new CertificateBuilder()
                    .subject("cn=localhost")
                    .setIsCertificateAuthority(true)
                    .buildSelfSigned();
            Random random = new Random();
            String prefix = random.ints(8, '1', 'Z')
                                  .boxed().map(Objects::toString).collect(Collectors.joining());
            String certChainPath = "/tmp/"+ prefix +"-chain.pem";
            String priKeyPath = "/tmp/"+ prefix +"-key.pem";
            nginx.withCopyToContainer(Transferable.of(x509Bundle.getCertificatePathPEM(),777),
                    certChainPath);
            nginx.withCopyToContainer(Transferable.of(x509Bundle.getPrivateKeyPEM(),777),
                    priKeyPath);

            nginx.withCopyToContainer(Transferable.of(TEST_CONTENT),"/tmp/nginx/1M.data");
            nginx.withCopyToContainer(Transferable.of(Strs.template(
                    """
                    user  nginx;
                    worker_processes  auto;
                    
                    error_log  /var/log/nginx/error.log notice;
                    pid        /run/nginx.pid;
                    
                    
                    events {
                        worker_connections  1024;
                    }
                    
                    
                    http {
                        include       /etc/nginx/mime.types;
                        default_type  application/octet-stream;
                    
                        log_format  main  '$remote_addr - $remote_user [$time_local] "$request" '
                                          '$status $body_bytes_sent "$http_referer" '
                                          '"$http_user_agent" "$http_x_forwarded_for"';
                    
                        access_log  /var/log/nginx/access.log  main;
                    
                        sendfile        on;
                        #tcp_nopush     on;
                    
                        keepalive_timeout  65;
                   
                    
                        include /etc/nginx/conf.d/*.conf;
                   
                        server {
                                listen 443 ssl ;
                                listen [::]:443 ssl ;
                                server_name test.ddnsp.org;
                                ssl_certificate ${cert};
                                ssl_certificate_key ${prikey};
                                #ssl_protocols TLSv1.2;
                                location / {
                                    root /tmp/nginx;
                                }
                        }
                    
                    }
                    """
            , Map.of("cert",certChainPath,"prikey",priKeyPath))),"/etc/nginx/nginx.conf");


            nginx.start();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

//    @Rule
//    public GenericContainer<?> nginx = new GenericContainer<>("nginx")
//            .withNetwork(CONTAINER_NETWORK)
//            .withNetworkAliases("nginx")
//            .withExposedPorts(80);


    public EndPoint createEndPoint(ProxyTransport proxyTransport, NetLocation location) throws Exception {

        var context=new ProxyContext(eventLoopGroup.next(),location);
        Future<EndPoint> future = proxyTransport.createEndPoint(context);
        future.sync();

        assert future.isSuccess();

        EndPoint endPoint = future.getNow();
        endPoint.setAutoRead(true);
        return endPoint;
    }

    private void writeToEndpoint(EmbeddedChannel channel,EndPoint endPoint){
        ByteBuf b = null;
        while ((b=channel.readOutbound())!=null){
            endPoint.write(b);
        }
        endPoint.flush();
    }

    private void readEndpointResponse(CompletableFuture<Void> future, EndPoint endPoint,EmbeddedChannel channel,ByteBuf result){
        endPoint.bufferHandler(buf->{
            channel.writeInbound(buf);
            Object read;
            while ((read=channel.readInbound())!=null){
                try {
                    System.out.println(read);
                    if (read instanceof HttpContent content){
                        result.writeBytes(content.content());
                    }
                    if (read instanceof LastHttpContent){
                        future.complete(null);
                    }
                } finally {
                    ReferenceCountUtil.safeRelease(read);
                }
            }
        });
        endPoint.readCompleteHandler(()->writeToEndpoint(channel,endPoint));
    }

    public void tcpTest(ProxyTransport proxyTransport,NetAddr targetAddress) throws Exception {

        NetLocation location = new NetLocation(new NetAddr(new InetSocketAddress("127.0.0.1",0)),
                targetAddress, TP.TCP);
        EmbeddedChannel channel = new EmbeddedChannel(
                SslContextBuilder.forClient().sslProvider(SslProvider.JDK).trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build().newHandler(UnpooledByteBufAllocator.DEFAULT),
//                new LoggingHandler(LogLevel.INFO),
                new HttpRequestEncoder(),new HttpResponseDecoder()
        );
        var header=new DefaultHttpHeaders()
                .add(HttpHeaderNames.HOST,"test.ddnsp.org")
                .add(HttpHeaderNames.ACCEPT,"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .add(HttpHeaderNames.CONNECTION,"close");

        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/1M.data",header);

        channel.writeOutbound(request);

        EndPoint endPoint = createEndPoint(proxyTransport,location);


        ByteBuf result = Unpooled.buffer(1024*1024);

        CompletableFuture<Void> future = new CompletableFuture<>();

        readEndpointResponse(future,endPoint,channel,result);

        writeToEndpoint(channel,endPoint);


        endPoint.closeFuture().addListener(f -> {
            System.out.println("endpoint closed");
            if (!future.isDone()){
                future.completeExceptionally(new TimeoutException());
            }
        });

        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof TimeoutException){
                Assert.fail("Endpoint closed before receive all data");
            }
        }

        Assert.assertEquals(TEST_CONTENT.length, result.readableBytes());
        Assert.assertEquals(Unpooled.wrappedBuffer(TEST_CONTENT),result);

        endPoint.close();

        Thread.sleep(500);
    }

    public void tcpTest(ProxyTransport proxyTransport) throws Exception {
        tcpTest(proxyTransport,new DomainNetAddr("nginx", 443));
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
        endPoint.flush();

        System.out.println();

        query = new DatagramDnsQuery(null, address,1, DnsOpCode.QUERY);
        query.setRecursionDesired(true);
        query.addRecord(DnsSection.QUESTION,new DefaultDnsQuestion("www.bilibili.com.",DnsRecordType.A));
        System.out.println(query);
        channel.writeOutbound(query);
        packet=channel.readOutbound();
        endPoint.write(packet);
        endPoint.flush();
        if (!countDownLatch.await(5,TimeUnit.SECONDS)){
            throw new RuntimeException("timeout..");
        }
        endPoint.close();

    }


}
