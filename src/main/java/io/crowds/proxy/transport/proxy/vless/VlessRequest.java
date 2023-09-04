package io.crowds.proxy.transport.proxy.vless;

import io.crowds.proxy.transport.Destination;

import java.util.UUID;

public class VlessRequest {
    private UUID id;
    private Destination destination;

    private Object payload;

    public VlessRequest(UUID id, Destination destination) {
        this.id = id;
        this.destination = destination;
    }

    public UUID getId() {
        return id;
    }

    public Destination getDestination() {
        return destination;
    }

    public VlessRequest setPayload(Object payload) {
        this.payload = payload;
        return this;
    }

    public Object getPayload() {
        return payload;
    }
}
