package io.crowds.proxy.transport.proxy.vless;

/**
 * The addons extension field of the VLESS request header (the Addons message of the former addons.proto).
 *
 * Field definitions:
 *   string Flow = 1;
 *   bytes  Seed = 2;
 */
public class Addons {
    private String flow;
    private byte[] seed;

    public String getFlow() {
        return flow;
    }

    public Addons setFlow(String flow) {
        this.flow = flow;
        return this;
    }

    public byte[] getSeed() {
        return seed;
    }

    public Addons setSeed(byte[] seed) {
        this.seed = seed;
        return this;
    }
}
