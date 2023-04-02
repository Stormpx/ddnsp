package proxy;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.proxy.transport.proxy.socks.SocksOption;
import io.crowds.proxy.transport.proxy.socks.SocksProxyTransport;
import org.junit.Test;

import java.net.InetSocketAddress;

public class SocksTest extends ProxyTest{

    protected ProxyTransport createProxy(ChannelCreator channelCreator) {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 1089);
        SocksOption option=new SocksOption()
                .setRemote(dest);
        option.setName("socks");
        return new SocksProxyTransport(channelCreator, option);
    }

    @Test
    public void tcpTest() throws Exception {
        super.tcpTest(createProxy(channelCreator));
    }


    @Test
    public void udpTest() throws Exception {
        super.udpTest(createProxy(channelCreator));
    }

}
