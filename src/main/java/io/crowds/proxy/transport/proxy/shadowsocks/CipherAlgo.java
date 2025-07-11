package io.crowds.proxy.transport.proxy.shadowsocks;

public enum CipherAlgo {


    AES_256_GCM(32,32,"aes-256-gcm"),
    AES_192_GCM(24,24,"aes-192-gcm"),
    AES_128_GCM(16,16,"aes-128-gcm"),
    CHACHA20_IETF_POLY1305(32,32,"chacha20-ietf-poly1305"),
    CHACHA20_IETF_POLY1305_2022(32,32,"2022-blake3-chacha20-poly1305"),
    AES_128_GCM_2022(16,16,"2022-blake3-aes-128-gcm"),
    AES_256_GCM_2022(32,32,"2022-blake3-aes-256-gcm"),
    ;
    private final int keySize;
    private final int saltSize;
    private final String name;

    CipherAlgo(int keySize, int saltSize, String name) {
        this.keySize = keySize;
        this.saltSize = saltSize;
        this.name=name;
    }

    public int getKeySize() {
        return keySize;
    }

    public int getSaltSize() {
        return saltSize;
    }

    public String getName() {
        return name;
    }

    public static CipherAlgo of(String name){
        for (CipherAlgo value : values()) {
            if (value.name.equalsIgnoreCase(name)){
                return value;
            }
        }
        return null;
    }
}
