package io.crowds.proxy.services.http;

import java.nio.file.Path;

public class HttpOption {
    private boolean enable;
    private String host;
    private Integer port;

    private Path cert;
    private Path key;
    private String keyPassword;

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

    public Path getCert() {
        return cert;
    }

    public HttpOption setCert(Path cert) {
        this.cert = cert;
        return this;
    }

    public Path getKey() {
        return key;
    }

    public HttpOption setKey(Path key) {
        this.key = key;
        return this;
    }

    public boolean isTls() {
        return cert!=null&&key!=null;
    }

    public String getKeyPassword() {
        return keyPassword;
    }

    public HttpOption setKeyPassword(String keyPassword) {
        this.keyPassword = keyPassword;
        return this;
    }
}
