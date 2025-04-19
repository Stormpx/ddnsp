package io.crowds.proxy.services.tun;

import io.vertx.core.json.JsonArray;

import java.util.List;

public class TunServerOption {
    private boolean enable;
    private String name;
    private int mtu=1500;
    private JsonArray ignoreAddress;

    public boolean isEnable() {
        return enable;
    }

    public TunServerOption setEnable(boolean enable) {
        this.enable = enable;
        return this;
    }

    public String getName() {
        return name;
    }

    public TunServerOption setName(String name) {
        this.name = name;
        return this;
    }

    public int getMtu() {
        return mtu;
    }

    public TunServerOption setMtu(int mtu) {
        this.mtu = mtu;
        return this;
    }

    public JsonArray getIgnoreAddress() {
        return ignoreAddress;
    }

    public TunServerOption setIgnoreAddress(JsonArray ignoreAddress) {
        this.ignoreAddress = ignoreAddress;
        return this;
    }
}
