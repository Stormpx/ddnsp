package io.crowds.proxy.transport;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.TP;

public record Destination(NetAddr addr, TP tp) {
    public Destination(NetLocation netLocation){
        this(netLocation.getDst(),netLocation.getTp());
    }
}
