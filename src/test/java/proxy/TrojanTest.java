package proxy;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.proxy.transport.TlsOption;
import io.crowds.proxy.transport.TransportOption;
import io.crowds.proxy.transport.proxy.shadowsocks.Cipher;
import io.crowds.proxy.transport.proxy.shadowsocks.ShadowsocksOption;
import io.crowds.proxy.transport.proxy.shadowsocks.ShadowsocksTransport;
import io.crowds.proxy.transport.proxy.trojan.TrojanOption;
import io.crowds.proxy.transport.proxy.trojan.TrojanProxyTransport;
import io.crowds.proxy.transport.ws.WsOption;
import org.junit.Test;

import java.net.InetSocketAddress;

public class TrojanTest extends ProxyTest{

    protected ProxyTransport createProxy(ChannelCreator channelCreator) {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16831);
        var option=new TrojanOption()
                .setAddress(dest)
                .setPassword("password")
                .setProtocol("trojan")
                .setTls(new TlsOption().setEnable(false))
                ;
        return new TrojanProxyTransport(channelCreator, (TrojanOption) option);
    }

    protected ProxyTransport createProxyWithWs(ChannelCreator channelCreator) {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16833);
        var option=new TrojanOption()
                .setAddress(dest)
                .setPassword("password")
                .setProtocol("trojan")
                .setNetwork("ws")
                .setTransport(new TransportOption().setWs(new WsOption()))
                .setTls(new TlsOption().setEnable(false))
                ;
        return new TrojanProxyTransport(channelCreator, (TrojanOption) option);

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
    public void websocketUdpTest() throws Exception {
        super.udpTest(createProxyWithWs(channelCreator));
    }
}
