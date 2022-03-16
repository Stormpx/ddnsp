package io.crowds.proxy.transport.proxy.vmess;

public enum  Option {
    CHUNK_STREAM(0x01),
    CHUNK_MASKING(0x04),
    GLOBAL_PADDING(0x08),
    AUTHENTICATED_LENGTH(0x10),
    ;

    private int value;

    Option(int i) {
        this.value=i;
    }

    public int getValue() {
        return value;
    }
}
