package proxy;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.proxy.transport.TransportOption;
import io.crowds.proxy.transport.proxy.vmess.Security;
import io.crowds.proxy.transport.proxy.vmess.User;
import io.crowds.proxy.transport.proxy.vmess.VmessOption;
import io.crowds.proxy.transport.proxy.vmess.VmessProxyTransport;
import io.crowds.proxy.transport.ws.WsOption;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.UUID;

public class VmessTest extends ProxyTest{

    protected ProxyTransport createProxy(ChannelCreator channelCreator) {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16823);
        var option=new VmessOption()
                .setAddress(dest)
                .setSecurity(Security.AES_128_GCM)
                .setUser(new User(UUID.fromString("b831381d-6324-4d53-ad4f-8cda48b30811"),0));
        return new VmessProxyTransport(channelCreator,option);
    }
    protected ProxyTransport createProxyWithWs(ChannelCreator channelCreator) {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16825);
        var option=new VmessOption()
                .setAddress(dest)
                .setSecurity(Security.AES_128_GCM)
                .setUser(new User(UUID.fromString("b831381d-6324-4d53-ad4f-8cda48b30811"),0))
                .setNetwork("ws")
                .setTransport(new TransportOption()
                        .setWs(new WsOption()));


        return new VmessProxyTransport(channelCreator, (VmessOption) option);
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
