package proxy;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.proxy.transport.proxy.ssh.SshOption;
import io.crowds.proxy.transport.proxy.ssh.SshProxyTransport;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

public class SshTest extends ProxyTestBase {

    @Rule
    public SshRule sshRule = new SshRule();
    protected ProxyTransport createProxy(ChannelCreator channelCreator) throws IOException, InterruptedException {
        SshOption sshOption = new SshOption();
        sshOption.setAddress(new InetSocketAddress("127.0.0.1",37432))
                .setUser("abc")
                .setPassword("password")
                .setVerifyStrategy(SshOption.VerifyStrategy.ACCEPT_ALL);
        sshOption.setName("ssh");
        sshRule.start(sshOption);
        return new SshProxyTransport(channelCreator, sshOption);
    }

    private void setupServer() throws IOException {
        SshServer sshServer = SshServer.setUpDefaultServer();
        sshServer.setHost("127.0.0.1");
        sshServer.setPort(37432);
        sshServer.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        sshServer.setPasswordAuthenticator(((username, password, session) -> {
            if ("abc".equals(username)||"password".equals(password)){
                return true;
            }else{
                return false;
            }
        }));
        sshServer.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);
        sshServer.start();


    }

    @Test
    public void tcpTest() throws Exception {
//        setupServer();
        super.tcpTest(createProxy(channelCreator));
    }

}
