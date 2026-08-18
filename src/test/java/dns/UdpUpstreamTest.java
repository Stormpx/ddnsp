package dns;

import io.crowds.dns.upstream.DnsUpstream;
import io.crowds.dns.upstream.UdpUpstream;
import io.crowds.util.DatagramChannelFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.net.InetSocketAddress;

public class UdpUpstreamTest extends DnsUpstreamTest{
    @Override
    protected DnsUpstream dnsUpstream() {
        MultiThreadIoEventLoopGroup eventLoopGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        return new UdpUpstream(eventLoopGroup.next(), DatagramChannelFactory.newFactory(NioDatagramChannel::new, NioDatagramChannel::new), new InetSocketAddress("114.114.114.114", 53));
    }
}
