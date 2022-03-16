package proxy;

import io.crowds.proxy.*;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.proxy.transport.TransportOption;
import io.crowds.proxy.transport.proxy.shadowsocks.Cipher;
import io.crowds.proxy.transport.proxy.shadowsocks.ShadowsocksOption;
import io.crowds.proxy.transport.proxy.shadowsocks.ShadowsocksTransport;
import io.crowds.proxy.transport.ws.WsOption;
import org.junit.Test;

import java.net.InetSocketAddress;

public class ShadowsocksTest extends ProxyTest {

    protected ProxyTransport createProxy(ChannelCreator channelCreator) {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16827);
        ShadowsocksOption option=new ShadowsocksOption()
                .setAddress(dest)
                .setCipher(Cipher.CHACHA20_IETF_POLY1305)
                .setPassword("passpasspass");
        option.setName("ss");
        return new ShadowsocksTransport(channelCreator, option);
    }

    protected ProxyTransport createProxyWithWs(ChannelCreator channelCreator) {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16829);
        var option=new ShadowsocksOption()
                .setAddress(dest)
                .setCipher(Cipher.CHACHA20_IETF_POLY1305)
                .setPassword("passpasspass")
                .setName("ss")
                .setNetwork("ws")
                .setTransport(new TransportOption().setWs(new WsOption()));
        return new ShadowsocksTransport(channelCreator, (ShadowsocksOption) option);

    }

    @Test
    public void tcpTest() throws Exception {
        super.tcpTest(createProxy(channelCreator));
    }

    @Test
    public void websocketTest() throws Exception {
        super.tcpTest(createProxyWithWs(channelCreator));
    }

    @Test
    public void udpTest() throws Exception {
        super.udpTest(createProxy(channelCreator));
    }
}
