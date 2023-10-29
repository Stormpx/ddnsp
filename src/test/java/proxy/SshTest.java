package proxy;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.proxy.transport.proxy.ssh.SshOption;
import io.crowds.proxy.transport.proxy.ssh.SshProxyTransport;
import io.crowds.proxy.transport.proxy.ssh.SshSession;
import io.crowds.util.Inet;
import org.junit.Test;

import java.net.InetSocketAddress;

public class SshTest extends ProxyTest{
    protected ProxyTransport createProxy(ChannelCreator channelCreator) {
        InetSocketAddress dest = Inet.createSocketAddress("127.0.0.1", 16827);
        SshOption sshOption = new SshOption();
        sshOption.setAddress(new InetSocketAddress("127.0.0.1",22))
                .setUser("root")
                .setPassword("111111111111111111")
                .setVerifyStrategy(SshOption.VerifyStrategy.ACCEPT_ALL);
        sshOption.setName("ssh");
        return new SshProxyTransport(channelCreator, sshOption);
    }

    @Test
    public void tcpTest() throws Exception {
        super.tcpTest(createProxy(channelCreator));
    }

}
