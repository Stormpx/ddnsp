package io.crowds.proxy.transport;

public class ProtocolOption {
    protected String name;
    protected String protocol;
    protected int connIdle=500;

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

    public int getConnIdle() {
        return connIdle;
    }

    public ProtocolOption setConnIdle(int connIdle) {
        this.connIdle = connIdle;
        return this;
    }
}
