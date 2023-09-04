package proxy;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.proxy.transport.TlsOption;
import io.crowds.proxy.transport.proxy.trojan.TrojanOption;
import io.crowds.proxy.transport.proxy.trojan.TrojanProxyTransport;
import io.crowds.proxy.transport.proxy.vless.VlessOption;
import io.crowds.proxy.transport.proxy.vless.VlessProxyTransport;
import io.crowds.proxy.transport.proxy.vless.VlessUUID;
import io.crowds.util.Rands;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.HexFormat;

import static org.junit.Assert.*;

public class VlessTest extends ProxyTest{

    protected ProxyTransport createProxy(ChannelCreator channelCreator) {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16839);
        var option=new VlessOption()
                .setAddress(dest)
                .setId(VlessUUID.of("testtest"))
                .setProtocol("vless")
                .setTls(new TlsOption().setEnable(false))
                ;
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
