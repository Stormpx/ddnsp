package dns;

import io.crowds.dns.upstream.DnsUpstream;
import io.crowds.dns.upstream.TcpUpstream;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;

public class TcpUpstreamTest extends DnsUpstreamTest{

    @Override
    protected DnsUpstream dnsUpstream() {
        MultiThreadIoEventLoopGroup eventLoopGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        return new TcpUpstream(eventLoopGroup, NioSocketChannel::new, new InetSocketAddress("114.114.114.114", 53));
    }


}
