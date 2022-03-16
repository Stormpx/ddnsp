package io.crowds.proxy.transport;

public class TlsOption {
    private boolean enable;
    private boolean allowInsecure=false;
    private String serverName;

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
}
