package io.crowds.proxy.services;

public class ServicesOption {
    private boolean enable;
    private String name;
    private String host;
    private Integer port;

    public boolean isEnable() {
        return enable;
    }

    public ServicesOption setEnable(boolean enable) {
        this.enable = enable;
        return this;
    }

    public String getHost() {
        return host;
    }

    public ServicesOption setHost(String host) {
        this.host = host;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public ServicesOption setPort(Integer port) {
        this.port = port;
        return this;
    }

    public String getName() {
        return name;
    }

    public ServicesOption setName(String name) {
        this.name = name;
        return this;
    }
}
