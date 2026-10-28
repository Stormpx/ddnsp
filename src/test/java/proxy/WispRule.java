package proxy;

import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.proxy.wisp.WispOption;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * a wisp server which is provided by <a href="https://github.com/MercuryWorkshop/wisp-js">wisp-js</a>.
 * there is no official docker image, so it will be built on demand.
 * <p>
 * wisp-js does not implement the authentication extensions ({@code 0x02} / {@code 0x03}) and the
 * stream open confirmation extension ({@code 0x05}), the authentication is covered by
 * {@link WispAuthTest} instead (it drives {@code WispConnection} through an {@code EmbeddedChannel}).
 */
public class WispRule extends AbstractRule {
    private static final Logger logger = LoggerFactory.getLogger(WispRule.class);

    public static final int WISP_PORT = 5001;

    /**
     * the image is shared between all the test instances, so that it is only built once per jvm.
     */
    private static final ImageFromDockerfile IMAGE = new ImageFromDockerfile("ddnsp-wisp-js", false)
            .withFileFromClasspath("Dockerfile", "wisp/Dockerfile");

    private final Map<String, Object> options = new LinkedHashMap<>();

    private String name;
    private GenericContainer<?> server;
    private WispOption inside;
    private WispOption outside;

    public WispRule(Network network) {
        super(network);
        options.put("allow_private_ips", true);
        options.put("parse_real_ip", false);
    }

    public WispRule setName(String name) {
        this.name = name;
        return this;
    }

    public WispRule wispVersion(int version) {
        options.put("wisp_version", version);
        return this;
    }

    public WispRule motd(String motd) {
        options.put("wisp_motd", motd);
        return this;
    }

    public WispRule allowUdp(boolean allow) {
        options.put("allow_udp_streams", allow);
        return this;
    }

    public WispOption getInside() {
        return inside;
    }

    public WispOption getOutside() {
        return outside;
    }

    public synchronized ProtocolOption start(ProtocolOption protocolOption) {
        if (server != null) {
            throw new IllegalStateException("wisp server already started");
        }
        if (!(protocolOption instanceof WispOption wispOption)) {
            throw new IllegalArgumentException(protocolOption.getClass().getName() + " is not supported");
        }
        if (name == null) {
            name = protocolOption.getName();
        }
        JsonObject wispOptions = new JsonObject();
        options.forEach(wispOptions::put);
        logger.info("wisp-js options: {}", wispOptions.encode());

        GenericContainer<?> container = new GenericContainer<>(IMAGE)
                .withNetwork(network)
                .withNetworkAliases(name)
                .withExposedPorts(WISP_PORT)
                .withLogConsumer(new Slf4jLogConsumer(logger).withPrefix("wisp-js"))
                .withCommand("-H", "0.0.0.0",
                        "-P", String.valueOf(WISP_PORT),
                        "-L", "DEBUG",
                        "-O", wispOptions.encode());
        this.server = container;
        container.start();

        this.inside = new WispOption(wispOption)
                .setAddress(InetSocketAddress.createUnresolved(name, WISP_PORT));
        this.outside = new WispOption(wispOption)
                .setAddress(new InetSocketAddress(server.getHost(), server.getMappedPort(WISP_PORT)));
        return outside;
    }

    @Override
    protected void after() {
        GenericContainer<?> server = this.server;
        if (server != null) {
            this.server = null;
            server.stop();
        }
    }
}
