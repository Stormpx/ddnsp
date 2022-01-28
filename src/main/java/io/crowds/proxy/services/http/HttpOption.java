package io.crowds.proxy.services.http;

public class HttpOption {
    private boolean enable;
    private String host;
    private Integer port;

    public boolean isEnable() {
        return enable;
    }

    public HttpOption setEnable(boolean enable) {
        this.enable = enable;
        return this;
    }

    public String getHost() {
        return host;
    }

    public HttpOption setHost(String host) {
        this.host = host;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public HttpOption setPort(Integer port) {
        this.port = port;
        return this;
    }
}
