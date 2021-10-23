package io.crowds.proxy.transport.vmess;

import java.util.UUID;

public class VmessDynamicPortCmd {
    private int port;
    private UUID uuid;
    private int alterId;
    private byte level;
    private short minuets;

    public VmessDynamicPortCmd(int port, UUID uuid, int alterId, byte level, short minuets) {
        this.port = port;
        this.uuid = uuid;
        this.alterId = alterId;
        this.level = level;
        this.minuets = minuets;
    }

    public int getPort() {
        return port;
    }

    public VmessDynamicPortCmd setPort(int port) {
        this.port = port;
        return this;
    }

    public UUID getUuid() {
        return uuid;
    }

    public VmessDynamicPortCmd setUuid(UUID uuid) {
        this.uuid = uuid;
        return this;
    }

    public int getAlterId() {
        return alterId;
    }

    public VmessDynamicPortCmd setAlterId(int alterId) {
        this.alterId = alterId;
        return this;
    }

    public byte getLevel() {
        return level;
    }

    public VmessDynamicPortCmd setLevel(byte level) {
        this.level = level;
        return this;
    }

    public short getMinuets() {
        return minuets;
    }

    public VmessDynamicPortCmd setMinuets(short minuets) {
        this.minuets = minuets;
        return this;
    }
}
