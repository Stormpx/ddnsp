import io.crowds.proxy.ChannelCreator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.*;

import java.net.InetSocketAddress;

public class UdpTest {

    public static void main(String[] args) throws InterruptedException {

        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();

        DatagramChannel channel = new NioDatagramChannel();
        channel.pipeline()
                .addLast(new DatagramDnsQueryEncoder())
                .addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
                        System.out.println(msg);
                    }
                })
        ;

        eventLoopGroup.next().register(channel);

        ChannelFuture f = channel.bind(new InetSocketAddress(0)).sync();

        assert f.isSuccess();

        InetSocketAddress dns1 = new InetSocketAddress("114.114.114.114",53);
        InetSocketAddress dns2 = new InetSocketAddress("8.8.8.8",53);
        DatagramDnsQuery query = new DatagramDnsQuery(null, dns1, 1);
        query.addRecord(DnsSection.QUESTION,new DefaultDnsQuestion("www.baidu.com",DnsRecordType.A));
        channel.writeAndFlush(query).sync();

        Thread.sleep(2000);

        query = new DatagramDnsQuery(null, dns2, 2);
        query.addRecord(DnsSection.QUESTION,new DefaultDnsQuestion("www.baidu.com",DnsRecordType.A));
        channel.writeAndFlush(query).sync();

        Thread.sleep(2000);


        query = new DatagramDnsQuery(null, dns1, 3);
        query.addRecord(DnsSection.QUESTION,new DefaultDnsQuestion("www.baidu.com",DnsRecordType.A));
        channel.writeAndFlush(query).sync();

        Thread.sleep(2000);

    }
}
