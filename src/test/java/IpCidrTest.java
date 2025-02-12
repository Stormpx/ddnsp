import io.crowds.proxy.dns.IpPool;
import io.crowds.util.IPCIDR;
import io.crowds.util.Rands;
import org.junit.Assert;
import org.junit.Test;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ThreadLocalRandom;

public class IpCidrTest {

    private byte[] ip(String ip) throws UnknownHostException {
        return InetAddress.getByName(ip).getAddress();
    }

    @Test
    public void test() throws UnknownHostException {

        try {
            new IPCIDR("192.168.31.0/33");
            Assert.fail();
        } catch (Exception e) {
        }
        try {
            new IPCIDR("192.168.31.0/-1");
            Assert.fail();
        } catch (Exception e) {
        }

        try {
            new IPCIDR("192.168.256.0/24");
            Assert.fail();
        } catch (Exception e) {
        }


        IPCIDR ipcidr = new IPCIDR("192.168.31.0/24");
        System.out.println(ipcidr);
        Assert.assertTrue(ipcidr.isMatch(ip("192.168.31.20")));
        Assert.assertFalse(ipcidr.isMatch(ip("192.168.30.20")));

        Assert.assertTrue(ipcidr.isMatch(ip("192.168.31.255")));
        Assert.assertTrue(ipcidr.isMatch(ip("192.168.31.0")));
        Assert.assertFalse(ipcidr.isMatch(ip("192.168.32.0")));

        ipcidr = new IPCIDR("10.10.1.32/27");
        System.out.println(ipcidr);
        Assert.assertTrue(ipcidr.isMatch(ip("10.10.1.44")));
        Assert.assertFalse(ipcidr.isMatch(ip("10.10.1.90")));

        System.out.println(new IPCIDR("0.0.0.0/0"));

        ipcidr=new IPCIDR("10.10.1.128/25");

        Assert.assertFalse(ipcidr.isMatch(ip("10.10.1.32")));
        Assert.assertTrue(ipcidr.isMatch(ip("10.10.1.128")));
        Assert.assertTrue(ipcidr.isMatch(ip("10.10.1.255")));
        Assert.assertFalse(ipcidr.isMatch(ip("10.10.1.127")));
        Assert.assertFalse(ipcidr.isMatch(ip("10.10.1.0")));


        ipcidr=new IPCIDR("10.10.1.44/31");

        Assert.assertFalse(ipcidr.isMatch(ip("10.10.1.46")));
    }


    @Test
    public void ipPoolTest(){
        String ip="192.10.1.44";


        for (int i = 4; i <= 32; i++) {
            IpPool ipPool = new IpPool(new IPCIDR(ip+"/" + i));
            long c=0;
            InetAddress address = ipPool.getAvailableAddress();
            while (address !=null){
//                System.out.println(address);
                c++;
                address=ipPool.getAvailableAddress();
            }
            System.out.println(i+" "+c);
            if (i<31) {
                Assert.assertEquals(Math.pow(2, 32 - i) - 2, c, 0.0);
            }else{
                Assert.assertEquals(0, c, 0.0);
            }
        }


    }
}
