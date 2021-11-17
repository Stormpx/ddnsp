package io.crowds.proxy.transport.vmess;

import java.util.UUID;

public class VmessUser {
    private UUID uuid;
    private byte[] cmdKey;
    private boolean primary;
    public VmessUser(UUID uuid, boolean primary) {
        this.uuid = uuid;
        this.primary = primary;
    }

    public boolean isPrimary() {
        return primary;
    }

    public UUID getUuid() {
        return uuid;
    }

    public VmessUser setUuid(UUID uuid) {
        this.uuid = uuid;
        return this;
    }

    public byte[] getCmdKey() {
        return cmdKey;
    }

    public VmessUser setCmdKey(byte[] cmdKey) {
        this.cmdKey = cmdKey;
        return this;
    }
}
