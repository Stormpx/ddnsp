package io.crowds.proxy.transport;

public class ProtocolOption {
    private String name;
    private String protocol;

    public String getName() {
        return name;
    }

    public ProtocolOption setName(String name) {
        this.name = name;
        return this;
    }

    public String getProtocol() {
        return protocol;
    }

    public ProtocolOption setProtocol(String protocol) {
        this.protocol = protocol;
        return this;
    }
}
