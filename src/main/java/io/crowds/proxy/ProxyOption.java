package io.crowds.proxy;

public class ProxyOption {
    private boolean enable=true;
    private String host;
    private Integer port;

    private String outHost;
    private Integer outPort;

    public String getHost() {
        return host;
    }

    public ProxyOption setHost(String host) {
        this.host = host;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public ProxyOption setPort(Integer port) {
        this.port = port;
        return this;
    }
}
