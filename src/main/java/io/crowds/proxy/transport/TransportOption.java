package io.crowds.proxy.transport;

import io.crowds.proxy.transport.ws.WsOption;

public class TransportOption {
    
    private WsOption ws;


    public WsOption getWs() {
        return ws;
    }

    public TransportOption setWs(WsOption ws) {
        this.ws = ws;
        return this;
    }
}
