package io.crowds.proxy.transport;

import java.util.List;

public class TlsOption {
    private boolean enable;
    private boolean warpHandler;
    private boolean allowInsecure=false;
    private List<String> alpn;
    private String serverName;

    public TlsOption() {
    }
    public TlsOption(TlsOption other) {
        this.enable = other.enable;
        this.allowInsecure = other.allowInsecure;
        this.serverName = other.serverName;
    }

    public boolean isWarpHandler() {
        return warpHandler;
    }

    public TlsOption setWarpHandler(boolean warpHandler) {
        this.warpHandler = warpHandler;
        return this;
    }

    public boolean isAllowInsecure() {
        return allowInsecure;
    }

    public TlsOption setAllowInsecure(boolean allowInsecure) {
        this.allowInsecure = allowInsecure;
        return this;
    }

    public String getServerName() {
        return serverName;
    }

    public TlsOption setServerName(String serverName) {
        this.serverName = serverName;
        return this;
    }


    public boolean isEnable() {
        return enable;
    }

    public TlsOption setEnable(boolean enable) {
        this.enable = enable;
        return this;
    }
    public List<String> getAlpn() {
        return alpn;
    }

    public TlsOption setAlpn(List<String> alpn) {
        this.alpn = alpn;
        return this;
    }
}
