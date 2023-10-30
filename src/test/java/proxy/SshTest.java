package proxy;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.proxy.transport.proxy.ssh.SshOption;
import io.crowds.proxy.transport.proxy.ssh.SshProxyTransport;
import io.crowds.proxy.transport.proxy.ssh.SshSession;
import io.crowds.util.Inet;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.channel.RequestHandler;
import org.apache.sshd.common.session.ConnectionService;
import org.apache.sshd.common.session.helpers.AbstractConnectionServiceRequestHandler;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.StaticPasswordAuthenticator;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.forward.DirectTcpipFactory;
import org.apache.sshd.server.forward.TcpipServerChannel;
import org.apache.sshd.server.global.TcpipForwardHandler;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

public class SshTest extends ProxyTest{
    protected ProxyTransport createProxy(ChannelCreator channelCreator) {
        SshOption sshOption = new SshOption();
        sshOption.setAddress(new InetSocketAddress("127.0.0.1",37432))
                .setUser("root")
                .setPassword("password")
                .setVerifyStrategy(SshOption.VerifyStrategy.ACCEPT_ALL);
        sshOption.setName("ssh");
        return new SshProxyTransport(channelCreator, sshOption);
    }

    private void setupServer() throws IOException {
        SshServer sshServer = SshServer.setUpDefaultServer();
        sshServer.setHost("127.0.0.1");
        sshServer.setPort(37432);
        sshServer.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        sshServer.setPasswordAuthenticator(((username, password, session) -> {
            if ("root".equals(username)||"password".equals(password)){
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
        setupServer();
        super.tcpTest(createProxy(channelCreator));
    }

}
