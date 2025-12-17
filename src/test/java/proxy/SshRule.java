package proxy;

import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.proxy.ssh.SshOption;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.Transferable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

public class SshRule extends AbstractRule {

    private static final Logger logger = LoggerFactory.getLogger(SshRule.class);
    private static final String SSHD_CONFIG = """
            PermitTunnel yes
            PasswordAuthentication yes
            PermitRootLogin yes
            AllowTcpForwarding yes
            PidFile /config/sshd.pid
            # override default of no subsystems
            Subsystem	sftp	internal-sftp
            Port 2222
            """;
    private GenericContainer<?> server;
    private String name;

    private SshOption inside;
    private SshOption outside;

    public SshRule(Network network) {
        super(network);
    }

    public SshRule setName(String name) {
        this.name = name;
        return this;
    }

    public SshOption getInside() {
        return inside;
    }

    public SshOption getOutside() {
        return outside;
    }

    public synchronized ProtocolOption start(SshOption sshOption) throws IOException, InterruptedException {
        if (server!=null){
            throw new IllegalStateException("Ssh server already started");
        }
        GenericContainer<?> container = new GenericContainer<>("linuxserver/openssh-server");
        if (name==null) {
            this.name = sshOption.getName();
        }
        int port = sshOption.getAddress()
                            .getPort();
        StringBuilder config = new StringBuilder(SSHD_CONFIG);

        container.withEnv("USER_NAME",sshOption.getUser())
                 .withEnv("USER_PASSWORD",sshOption.getPassword())
                 .withEnv("SUDO_ACCESS","true")
                 .withEnv("PASSWORD_ACCESS","true")
                 .withNetwork(network)
                 .withNetworkAliases(name)
                 .withCopyToContainer(Transferable.of(config.toString(), Transferable.DEFAULT_FILE_MODE),"/config/sshd/sshd_config")
                 .withLogConsumer(new Slf4jLogConsumer(logger))
                 .withAccessToHost(true)
                 .setPortBindings(List.of(port +":2222"));


        server = container;
        server.start();

        this.inside = new SshOption(sshOption).setAddress(new InetSocketAddress(name,2222));
        this.outside = sshOption;
        return sshOption;
    }



    @Override
    protected void after() {
        if (server!=null){
            server.stop();
            server=null;
        }
    }

}
