package io.crowds.proxy.transport.proxy.vless;

import io.crowds.proxy.transport.Destination;

import java.util.UUID;

public class VlessRequest {
    private UUID id;
    private Destination destination;
    private Vless.Flow flow;
    private AddonsOuterClass.Addons addons;


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

    public AddonsOuterClass.Addons getAddons() {
        return addons;
    }

    public VlessRequest setAddons(AddonsOuterClass.Addons addons) {
        this.addons = addons;
        return this;
    }

    public Vless.Flow getFlow() {
        return flow;
    }

    public VlessRequest setFlow(Vless.Flow flow) {
        this.flow = flow;
        return this;
    }
}
