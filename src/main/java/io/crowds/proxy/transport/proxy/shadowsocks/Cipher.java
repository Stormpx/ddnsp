package io.crowds.proxy.transport.proxy.shadowsocks;

public enum  Cipher {

    CHACHA20_IETF_POLY1305(32,32,"chacha20-ietf-poly1305"),
    AES_256_GCM(32,32,"aes-256-gcm"),
    AES_192_GCM(24,24,"aes-192-gcm"),
    AES_128_GCM(16,16,"aes-128-gcm"),
    ;
    private int keySize;
    private int saltSize;
    private String name;

    Cipher(int keySize, int saltSize,String name) {
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


    public static Cipher of(String name){
        for (Cipher value : values()) {
            if (value.name.equalsIgnoreCase(name)){
                return value;
            }
        }
        return null;
    }
}
