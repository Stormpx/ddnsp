package io.crowds.proxy.common.sniff;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.util.Async;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class HostnameSniffer {
    private static final Exception PORTS_EXCEPTION = new RuntimeException("Mismatch the port range");
    private static final Exception IGNORED_HOSTNAME_EXCEPTION = new RuntimeException("Ignored hostname");

    private final SniffOption sniffOption;

    public HostnameSniffer(SniffOption sniffOption) {
        this.sniffOption = sniffOption;
    }

    public SniffOption getOption() {
        return sniffOption;
    }

    public Future<String> sniff(Channel channel, NetLocation netLocation) {
        NetAddr dst = netLocation.getDst();
        SniffOption sniffOption = this.sniffOption;
        Set<Integer> ports = Objects.requireNonNullElse(sniffOption.getPorts(),Set.of());
        if (!ports.contains(dst.getPort())){
            return channel.eventLoop().newFailedFuture(PORTS_EXCEPTION);
        }

        Promise<String> promise = channel.eventLoop().newPromise();
        Async.cascadeFailure(SniSniffingHandler.sniffHostname(channel,500),promise,
                f->{
                    String hostname = f.get();
                    Set<String> ignored = Objects.requireNonNullElse(sniffOption.getIgnoreDomain(), Set.of());
                    if (ignored.contains(hostname)){
                        promise.setFailure(IGNORED_HOSTNAME_EXCEPTION);
                        return;
                    }
                    promise.setSuccess(hostname);
                });

        return promise;
    }

}
