package io.crowds.proxy.transport.proxy.vless;

/**
 * VLESS 请求头中的 addons 扩展字段（对应原 addons.proto 中的 Addons 消息）。
 *
 * 字段定义：
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
