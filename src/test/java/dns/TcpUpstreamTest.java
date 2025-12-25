package dns;

import io.crowds.dns.DnsCli;
import io.crowds.dns.DnsUpstream;
import io.crowds.dns.TcpUpstream;
import io.crowds.dns.UdpUpstream;
import io.crowds.dns.cache.DnsCache;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.SingleThreadIoEventLoop;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.dns.*;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class TcpUpstreamTest extends DnsUpstreamTest{

    @Override
    protected DnsUpstream dnsUpstream() {
        MultiThreadIoEventLoopGroup eventLoopGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        return new TcpUpstream(eventLoopGroup, NioSocketChannel::new, new InetSocketAddress("114.114.114.114", 53));
    }


}
