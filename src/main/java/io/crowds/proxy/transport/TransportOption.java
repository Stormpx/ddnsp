package io.crowds.proxy.transport;

import io.crowds.proxy.transport.ws.WsOption;

public class TransportOption {

    private String dev;
    private WsOption ws;

    public String getDev() {
        return dev;
    }

    public TransportOption setDev(String dev) {
        this.dev = dev;
        return this;
    }

    public WsOption getWs() {
        return ws;
    }

    public TransportOption setWs(WsOption ws) {
        this.ws = ws;
        return this;
    }


}
