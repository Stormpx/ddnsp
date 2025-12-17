package proxy;

import org.junit.rules.ExternalResource;
import org.testcontainers.containers.Network;

public class AbstractRule extends ExternalResource {

    protected final Network network;

    public AbstractRule(Network network) {
        this.network = network;
    }
}
