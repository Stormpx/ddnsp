package proxy;

import io.crowds.proxy.*;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.proxy.transport.TransportOption;
import io.crowds.proxy.transport.proxy.shadowsocks.CipherAlgo;
import io.crowds.proxy.transport.proxy.shadowsocks.ShadowsocksOption;
import io.crowds.proxy.transport.proxy.shadowsocks.ShadowsocksTransport;
import io.crowds.proxy.transport.ws.WsOption;
import io.crowds.util.Inet;
import org.junit.Test;

import java.net.InetSocketAddress;

public class ShadowsocksTest extends ProxyTest {

    protected ProxyTransport createProxy(ChannelCreator channelCreator) {
        InetSocketAddress dest = Inet.createSocketAddress("127.0.0.1", 16827);
        ShadowsocksOption option=new ShadowsocksOption()
                .setAddress(dest)
                .setCipher(CipherAlgo.CHACHA20_IETF_POLY1305)
                .setPassword("passpasspass");
        option.setName("ss");
        return new ShadowsocksTransport(channelCreator, option);
    }

    protected ProxyTransport createProxyWithWs(ChannelCreator channelCreator) {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16829);
        var option=new ShadowsocksOption()
                .setAddress(dest)
                .setCipher(CipherAlgo.CHACHA20_IETF_POLY1305)
                .setPassword("passpasspass")
                .setName("ss")
                .setNetwork("ws")
                .setTransport(new TransportOption().setWs(new WsOption()));
        return new ShadowsocksTransport(channelCreator, (ShadowsocksOption) option);
    }


    protected ProxyTransport createProxy2022(ChannelCreator channelCreator) {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16835);
        ShadowsocksOption option=new ShadowsocksOption()
                .setAddress(dest)
                .setCipher(CipherAlgo.AES_256_GCM_2022)
                .setPassword("LYfdF9Ka9TdphHJTKY0zkGB8UqnPpvVrDnNYnmILvRA=");
        option.setName("ss");
        return new ShadowsocksTransport(channelCreator, option);
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

    @Test
    public void tcp2022Test() throws Exception {
        super.tcpTest(createProxy2022(channelCreator));
    }


    @Test
    public void udp2022Test() throws Exception {
        super.udpTest(createProxy2022(channelCreator));
    }
}
