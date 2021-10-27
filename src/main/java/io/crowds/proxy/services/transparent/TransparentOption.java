package io.crowds.proxy.services.transparent;

public class TransparentOption {
    private boolean enable;
    private String host;
    private Integer port;

    public boolean isEnable() {
        return enable;
    }

    public TransparentOption setEnable(boolean enable) {
        this.enable = enable;
        return this;
    }

    public String getHost() {
        return host;
    }

    public TransparentOption setHost(String host) {
        this.host = host;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public TransparentOption setPort(Integer port) {
        this.port = port;
        return this;
    }
}
