package proxy;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.proxy.transport.TlsOption;
import io.crowds.proxy.transport.proxy.vless.VlessOption;
import io.crowds.proxy.transport.proxy.vless.VlessProxyTransport;
import io.crowds.proxy.transport.proxy.vless.VlessUUID;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.junit.Assert.*;

public class VlessTest extends ProxyTestBase {

    @Rule
    public XrayRule xrayRule = new XrayRule();

    protected ProxyTransport createProxy(ChannelCreator channelCreator) throws IOException {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16839);
        var option= xrayRule.start(new VlessOption()
                .setAddress(dest)
                .setId("testtest")
                .setProtocol("vless")
                .setTls(new TlsOption().setEnable(false)));
        return new VlessProxyTransport(channelCreator, (VlessOption) option);
    }

    @Test
    public void uuidTest(){
//        System.out.println(HexFormat.ofDelimiter("-").formatHex(Rands.genBytes(8),0,4));
        assertEquals("feb54431-301b-52bb-a6dd-e1e93e81bb9e",VlessUUID.of("example").toString());
        assertEquals("5783a3e7-e373-51cd-8642-c83782b807c5",VlessUUID.of("我爱\uD83C\uDF49老师1314").toString());
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
