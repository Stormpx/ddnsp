package io.crowds.api;

public class ApiOption {

    private boolean enable;
    private String host;
    private Integer port;
    private String token;

    public boolean isEnable() {
        return enable;
    }

    public ApiOption setEnable(boolean enable) {
        this.enable = enable;
        return this;
    }

    public String getHost() {
        return host;
    }

    public ApiOption setHost(String host) {
        this.host = host;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public ApiOption setPort(Integer port) {
        this.port = port;
        return this;
    }

    public String getToken() {
        return token;
    }

    public ApiOption setToken(String token) {
        this.token = token;
        return this;
    }
}
