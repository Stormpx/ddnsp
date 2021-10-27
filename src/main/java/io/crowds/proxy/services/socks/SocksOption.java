package io.crowds.proxy.services.socks;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.crowds.Strs;

public class SocksOption {
    private boolean enable;

    private String host;
    private Integer port;
    private String username;
    private String password;

    @JsonIgnore
    public boolean isPassAuth(){
        return !Strs.isBlank(username)&&!Strs.isBlank(password);
    }

    public boolean isEnable() {
        return enable;
    }

    public SocksOption setEnable(boolean enable) {
        this.enable = enable;
        return this;
    }

    public String getHost() {
        return host;
    }

    public SocksOption setHost(String host) {
        this.host = host;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public SocksOption setPort(Integer port) {
        this.port = port;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public SocksOption setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public SocksOption setPassword(String password) {
        this.password = password;
        return this;
    }
}
