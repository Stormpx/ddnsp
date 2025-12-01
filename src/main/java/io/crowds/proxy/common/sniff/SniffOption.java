package io.crowds.proxy.common.sniff;

import java.util.Set;

public class SniffOption {

    private Set<Integer> ports;

    private Set<String> ignoreDomain;

    public Set<String> getIgnoreDomain() {
        return ignoreDomain;
    }

    public SniffOption setIgnoreDomain(Set<String> ignoreDomain) {
        this.ignoreDomain = ignoreDomain;
        return this;
    }

    public Set<Integer> getPorts() {
        return ports;
    }

    public SniffOption setPorts(Set<Integer> ports) {
        this.ports = ports;
        return this;
    }
}
