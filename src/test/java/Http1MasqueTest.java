import io.crowds.compoments.capsule.Capsule;
import io.crowds.compoments.capsule.CapsuleDecoder;
import io.crowds.compoments.capsule.CapsuleEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.dns.DnsRecordType;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * HTTP/1.1 MASQUE connect-udp test.
 *
 * Connects to an HTTP/1.1 proxy at 127.0.0.1:19999, upgrades to connect-udp,
 * then sends a DNS query for www.baidu.com to 114.114.114.114:53 through the
 * UDP-over-HTTP tunnel using the Capsule Protocol (RFC 9297).
 *
 * Usage: start the proxy server first, then run this class.
 */
public class Http1MasqueTest {

    private static final String PROXY_HOST = "127.0.0.1";
    private static final int PROXY_PORT = 19999;
    private static final String TARGET_HOST = "doh.pub";
    private static final int TARGET_PORT = 53;
    private static final String DNS_NAME = "www.baidu.com";
    private static final String URI_PATH = "/.well-known/masque/udp";
    //0:path 1:query_kv 2:query_,
    private static final int URI_STYLE= 2;

    public static void main(String[] args) throws Exception {
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        CountDownLatch latch = new CountDownLatch(1);

        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioSocketChannel.class)
             .handler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ch.pipeline().addLast(new HttpClientCodec());
                     ch.pipeline().addLast(new HttpObjectAggregator(65536));
                     ch.pipeline().addLast(new MasqueClientHandler(latch));
                 }
             });

            System.out.println("Connecting to proxy " + PROXY_HOST + ":" + PROXY_PORT);
            Channel channel = b.connect(PROXY_HOST, PROXY_PORT).sync().channel();
            System.out.println("Connected. Sending connect-udp upgrade request...");

            // Build the MASQUE connect-udp upgrade request per RFC 9298 Section 3.2
            // URI: /.well-known/masque/udp/{target_host}/{target_port}/
            String requestUri;
            if (URI_STYLE==0){
                requestUri = URI_PATH + "/" + TARGET_HOST + "/" + TARGET_PORT + "/";
            }else if (URI_STYLE==1){
                requestUri = URI_PATH + "?h=" + TARGET_HOST + "&p=" + TARGET_PORT;
            }else if (URI_STYLE==2){
                requestUri = URI_PATH + "?" + TARGET_HOST + "," + TARGET_PORT;
            }else{
                return;
            }

            FullHttpRequest request = new DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1,
                    HttpMethod.GET,
                    requestUri
            );
            request.headers()
                    .set(HttpHeaderNames.HOST, PROXY_HOST)
                    .set(HttpHeaderNames.CONNECTION, "Upgrade")
                    .set(HttpHeaderNames.UPGRADE, "connect-udp")
                    .set("Capsule-Protocol", "?1");

            channel.write(request);
            channel.flush();
            int c = 0;
            while (!latch.await(1,TimeUnit.SECONDS)){
                c++;
                if (c==10){
                    System.err.println("Timeout waiting for DNS response");
                    break;
                }
                if (!channel.isActive()){
                    System.err.println("Connection closed");
                    break;
                }
            }


            Thread.sleep(500);
        } finally {
            group.shutdownGracefully().sync();
        }
    }

    /**
     * Builds a raw DNS A-record query packet for the given domain name.
     * Wire format: [ID][FLAGS][QDCOUNT][ANCOUNT][NSCOUNT][ARCOUNT][QUESTION...]
     */
    private static ByteBuf buildDnsQuery(String domainName) {
        ByteBuf buf = Unpooled.buffer(512);

        // Header
        int id = (int) (Math.random() * 0xFFFF);
        buf.writeShort(id);                  // Transaction ID
        buf.writeShort(0x0100);              // Flags: standard query, recursion desired
        buf.writeShort(1);                   // QDCOUNT = 1
        buf.writeShort(0);                   // ANCOUNT = 0
        buf.writeShort(0);                   // NSCOUNT = 0
        buf.writeShort(0);                   // ARCOUNT = 0

        // Question section: encode domain name as labels
        for (String label : domainName.split("\\.")) {
            byte[] bytes = label.getBytes(StandardCharsets.UTF_8);
            buf.writeByte(bytes.length);
            buf.writeBytes(bytes);
        }
        buf.writeByte(0);                    // Root label terminator
        buf.writeShort(DnsRecordType.A.intValue());  // QTYPE = A (1)
        buf.writeShort(1);                   // QCLASS = IN (1)

        System.out.println("DNS Query ID: 0x" + Integer.toHexString(id));
        return buf;
    }

    /**
     * Decodes a raw DNS response ByteBuf and prints the results.
     */
    private static void decodeDnsResponse(ByteBuf buf) {
        try {
            // Header
            int id = buf.readUnsignedShort();
            int flags = buf.readUnsignedShort();
            boolean isResponse = (flags & 0x8000) != 0;
            int rcode = flags & 0x000F;
            int qdCount = buf.readUnsignedShort();
            int anCount = buf.readUnsignedShort();
            int nsCount = buf.readUnsignedShort();
            int arCount = buf.readUnsignedShort();

            System.out.println("=== DNS Response ===");
            System.out.println("ID: 0x" + Integer.toHexString(id));
            System.out.println("Is Response: " + isResponse);
            System.out.println("RCode: " + rcode + (rcode == 0 ? " (NOERROR)" : " (ERROR)"));
            System.out.println("Questions: " + qdCount + ", Answers: " + anCount
                    + ", Authority: " + nsCount + ", Additional: " + arCount);

            // Questions
            for (int i = 0; i < qdCount; i++) {
                String qname = readDnsName(buf);
                int qtype = buf.readUnsignedShort();
                int qclass = buf.readUnsignedShort();
                System.out.println("  Question: " + qname + " TYPE=" + qtype + " CLASS=" + qclass);
            }

            // Answers
            for (int i = 0; i < anCount; i++) {
                String name = readDnsName(buf);
                int type = buf.readUnsignedShort();
                int cls = buf.readUnsignedShort();
                long ttl = buf.readUnsignedInt();
                int rdLength = buf.readUnsignedShort();

                if (type == DnsRecordType.A.intValue() && rdLength == 4) {
                    System.out.println("  Answer: " + name + " A "
                            + buf.readUnsignedByte() + "." + buf.readUnsignedByte() + "."
                            + buf.readUnsignedByte() + "." + buf.readUnsignedByte()
                            + " TTL=" + ttl);
                } else if (type == DnsRecordType.AAAA.intValue() && rdLength == 16) {
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < 8; j++) {
                        if (j > 0) sb.append(":");
                        sb.append(String.format("%x", buf.readUnsignedShort()));
                    }
                    System.out.println("  Answer: " + name + " AAAA " + sb + " TTL=" + ttl);
                } else {
                    System.out.println("  Answer: " + name + " TYPE=" + type + " LEN=" + rdLength + " TTL=" + ttl);
                    buf.skipBytes(rdLength);
                }
            }

            // Skip authority and additional
            for (int i = 0; i < nsCount + arCount; i++) {
                readDnsName(buf);
                buf.readUnsignedShort(); // type
                buf.readUnsignedShort(); // class
                buf.readUnsignedInt();   // ttl
                int rdLen = buf.readUnsignedShort();
                buf.skipBytes(rdLen);
            }

            System.out.println("=== End DNS Response ===");
        } catch (Exception e) {
            System.err.println("Failed to decode DNS response: " + e.getMessage());
            e.printStackTrace();
            buf.readerIndex(0);
            System.out.print("Raw response hex: ");
            while (buf.isReadable()) {
                System.out.printf("%02x ", buf.readUnsignedByte());
            }
            System.out.println();
        }
    }

    /**
     * Reads a DNS name from wire format (supports compression pointers per RFC 1035).
     */
    private static String readDnsName(ByteBuf buf) {
        StringBuilder name = new StringBuilder();
        int len;
        boolean jumped = false;
        int jumpOffset = -1;

        while ((len = buf.readUnsignedByte()) != 0) {
            if ((len & 0xC0) == 0xC0) {
                if (!jumped) {
                    jumpOffset = buf.readerIndex() + 1;
                }
                int pointer = ((len & 0x3F) << 8) | buf.readUnsignedByte();
                buf.readerIndex(pointer);
                jumped = true;
            } else {
                if (name.length() > 0) name.append('.');
                byte[] label = new byte[len];
                buf.readBytes(label);
                name.append(new String(label, StandardCharsets.UTF_8));
            }
        }

        if (jumped && jumpOffset >= 0) {
            buf.readerIndex(jumpOffset);
        }

        return name.toString();
    }

    /**
     * Handles the HTTP 101 Switching Protocols response and initiates the DNS query.
     */
    private static class MasqueClientHandler extends ChannelInboundHandlerAdapter {
        private final CountDownLatch latch;

        MasqueClientHandler(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof FullHttpResponse response) {
                if (response.status().code() == HttpResponseStatus.SWITCHING_PROTOCOLS.code()) {
                    System.out.println("Upgrade successful: " + response.status());
                    System.out.println("  Connection: " + response.headers().get(HttpHeaderNames.CONNECTION));
                    System.out.println("  Upgrade: " + response.headers().get(HttpHeaderNames.UPGRADE));
                    System.out.println("  Capsule-Protocol: " + response.headers().get("Capsule-Protocol"));

                    // Replace pipeline: remove HTTP codecs, add Capsule codecs
                    ctx.pipeline().remove(HttpClientCodec.class);
                    ctx.pipeline().remove(HttpObjectAggregator.class);
                    ctx.pipeline().addLast(new CapsuleDecoder(65527));
                    ctx.pipeline().addLast(new CapsuleEncoder());
                    ctx.pipeline().addLast(new CapsuleDnsHandler(latch));
                    ctx.pipeline().remove(this);

                    // Build and send DNS query as a Capsule datagram
                    ByteBuf dnsQuery = buildDnsQuery(DNS_NAME);
                    System.out.println(
                            "Sending DNS query for " + DNS_NAME + " (" + dnsQuery.readableBytes() + " bytes)");
                    ctx.channel().writeAndFlush(Capsule.datagram(dnsQuery));
                } else {
                    System.err.println("Upgrade failed: " + response.status());
                    latch.countDown();
                    ctx.close();
                }
            } else {
                super.channelRead(ctx, msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("Client error: " + cause.getMessage());
            cause.printStackTrace();
            latch.countDown();
            ctx.close();
        }
    }

    /**
     * Receives Capsule datagrams (DNS responses) after the tunnel is established.
     */
    private static class CapsuleDnsHandler extends SimpleChannelInboundHandler<Capsule> {
        private final CountDownLatch latch;

        CapsuleDnsHandler(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Capsule capsule) {
            if (capsule.type() == Capsule.TYPE_DATAGRAM) {
                ByteBuf content = capsule.content();
                System.out.println("Received Capsule DATAGRAM (" + content.readableBytes() + " bytes)");
                ByteBuf data = content.retainedDuplicate();
                try {
                    decodeDnsResponse(data);
                } finally {
                    data.release();
                }
                latch.countDown();
                ctx.close();
            } else {
                System.out.println("Received unknown capsule type: " + capsule.type());
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("Capsule handler error: " + cause.getMessage());
            cause.printStackTrace();
            latch.countDown();
            ctx.close();
        }
    }
}
