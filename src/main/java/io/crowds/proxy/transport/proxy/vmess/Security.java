package io.crowds.proxy.transport.proxy.vmess;

public enum Security {
    //legacy not support
//    AES_128_CFB((byte)0),
    NONE((byte)5,"none"),
    AES_128_GCM((byte)3,"aes-128-gcm"),
    ChaCha20_Poly1305((byte)4,"chacha20-poly1305");

    private byte value;
    private String name;

    Security(byte value, String name) {
        this.value = value;
        this.name=name;
    }

    public byte getValue() {
        return value;
    }

    public static Security of(String name){
        for (Security value : values()) {
            if (value.name.equalsIgnoreCase(name)){
                return value;
            }
        }
        return NONE;
    }
}
