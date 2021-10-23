package io.crowds.proxy.transport.vmess;

public enum Security {
    //legacy not support
//    AES_128_CFB((byte)0),
    NONE((byte)5),
    AES_128_GCM((byte)3),
    ChaCha20_Poly1305((byte)4);

    private byte value;

    Security(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }
}
