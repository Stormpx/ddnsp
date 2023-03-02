package router;

import io.crowds.proxy.routing.LinearRouter;
import io.crowds.proxy.routing.Router;
import io.crowds.util.Inet;
import io.netty.util.NetUtil;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;

public abstract class RouterTest {
    protected abstract Router setupRouter(List<String> rules);

    @Test
    public void test1() throws UnknownHostException {
        var rules = List.of(
                "eq;www.abc.domain.com;ok",
                "domain;abc.domain.com;oo",
                "kw;domain1;ok",
                "domain;google.com;ok",
                "ew;abc;ok",
                "cidr;170.0.0.0/8;ok",
                "cidr;91.108.56.0/22;okk"
        );
        Router router = setupRouter(rules);
        InetSocketAddress src = new InetSocketAddress(InetAddress.getLocalHost(), 1);
        for (int i = 0; i < 5; i++) {
            Assert.assertEquals("ok",router.routing(src,"www.abc.domain.com"));
            Assert.assertEquals("oo",router.routing(src,"abc.domain.com"));
            Assert.assertNull(router.routing(src,"domain.com"));
        }

        for (int i = 0; i < 5; i++) {
            Assert.assertEquals("ok",router.routing(src,"a.b.cdomain1.com"));
            Assert.assertNull(router.routing(src,"a.b.cdomain.com"));
        }

        for (int i = 0; i < 5; i++) {
            Assert.assertEquals("ok",router.routing(src,"google.com"));
            Assert.assertEquals("ok",router.routing(src,"a.bc.s.dwa.fwa.google.com"));
            Assert.assertNull(router.routing(src,".com"));
            Assert.assertNull(router.routing(src,"google.org"));
        }

        for (int i = 0; i < 5; i++) {
            Assert.assertEquals("ok",router.routing(src,"google.abc"));
            Assert.assertNull(router.routing(src,"google.org"));
            Assert.assertNull(router.routing(src,"google"));
        }
        for (int i = 0; i < 5; i++) {
            Assert.assertEquals("ok",router.routingIp(NetUtil.createInetAddressFromIpAddressString("170.23.43.12"),true));
            Assert.assertNull(router.routingIp(NetUtil.createInetAddressFromIpAddressString("127.0.0.1"),true));
            Assert.assertNull(router.routingIp(NetUtil.createInetAddressFromIpAddressString("171.0.0.0"),true));
            Assert.assertEquals("okk",router.routingIp(NetUtil.createInetAddressFromIpAddressString("91.108.57.0"),true));
        }

    }

}
