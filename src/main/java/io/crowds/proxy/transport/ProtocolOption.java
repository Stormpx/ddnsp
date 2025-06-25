package io.crowds.proxy.transport;

public class ProtocolOption{
    protected String name;
    protected String protocol;
    protected int connIdle;
    protected TlsOption tls;
    protected String network;
    protected TransportOption transport;

    public ProtocolOption() {

    }

    public ProtocolOption(ProtocolOption other) {
        this.name = other.name;
        this.protocol = other.protocol;
        this.connIdle = other.connIdle;
        this.tls = other.tls==null?null:new TlsOption(other.tls);
        this.network = other.network;
        this.transport = other.transport==null?null:new TransportOption(other.transport);
    }

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

    public TlsOption getTls() {
        return tls;
    }

    public ProtocolOption setTls(TlsOption tls) {
        this.tls = tls;
        return this;
    }

    public TransportOption getTransport() {
        return transport;
    }

    public ProtocolOption setTransport(TransportOption transport) {
        this.transport = transport;
        return this;
    }

    public String getNetwork() {
        return network;
    }

    public ProtocolOption setNetwork(String network) {
        this.network = network;
        return this;
    }

}
