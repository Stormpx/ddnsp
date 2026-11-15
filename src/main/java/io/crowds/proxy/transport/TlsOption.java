package io.crowds.proxy.transport;

import io.crowds.compoments.tls.reality.RealityOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TlsOption {
    private boolean enable;
    private boolean allowInsecure=false;
    private List<String> alpn;
    private String serverName;
    private RealityOption reality;

    public TlsOption() {
    }
    public TlsOption(TlsOption other) {
        this.enable = other.enable;
        this.allowInsecure = other.allowInsecure;
        this.serverName = other.serverName;
        this.alpn = new ArrayList<>(Objects.requireNonNullElse(other.alpn,List.of()));
        this.reality = other.reality == null ? null : new RealityOption(other.reality);
    }

    public RealityOption getReality() {
        return reality;
    }

    public boolean isReality() {
        return reality != null;
    }

    public TlsOption setReality(RealityOption reality) {
        this.reality = reality;
        if (reality != null) {
            this.enable = true;
        }
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
