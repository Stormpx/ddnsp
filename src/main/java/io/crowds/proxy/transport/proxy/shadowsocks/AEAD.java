package io.crowds.proxy.transport.proxy.shadowsocks;

import io.crowds.util.Crypto;
import io.crowds.util.Hash;
import io.crowds.util.Rands;

import java.nio.charset.StandardCharsets;

public class AEAD {

    public static byte[] genSalt(Cipher cipher){
        return Rands.genBytes(cipher.getSaltSize());
    }

    public static byte[] genSubKey(Cipher cipher,byte[] masterKey,byte[] salt){
        return switch (cipher){
            case CHACHA20_IETF_POLY1305,AES_128_GCM,AES_192_GCM,AES_256_GCM ->Hash.hkdfSHA1(masterKey,salt,"ss-subkey".getBytes(),cipher.getKeySize());
            case AES_128_GCM_2022,AES_256_GCM_2022 -> {
                byte[] material = new byte[masterKey.length+salt.length];
                System.arraycopy(masterKey,0,material,0,masterKey.length);
                System.arraycopy(salt,0,material,masterKey.length,salt.length);
                yield Hash.blake3(material,"shadowsocks 2022 session subkey".getBytes());
            }
        };

    }
    public static javax.crypto.Cipher getEncryptCipher(Cipher cipher,byte[] key,byte[] nonce) throws Exception {
        return switch (cipher) {
            case CHACHA20_IETF_POLY1305 -> Crypto.getChaCha20Poly1305Cipher( key, nonce,true);
            case AES_256_GCM, AES_192_GCM, AES_128_GCM,AES_128_GCM_2022,AES_256_GCM_2022 -> Crypto.getGcmCipher(key, nonce,true);
        };
    }

    public static javax.crypto.Cipher getDecryptCipher(Cipher cipher,byte[] key,byte[] nonce) throws Exception {
        return switch (cipher) {
            case CHACHA20_IETF_POLY1305 -> Crypto.getChaCha20Poly1305Cipher( key, nonce,false);
            case AES_256_GCM, AES_192_GCM, AES_128_GCM,AES_128_GCM_2022,AES_256_GCM_2022 -> Crypto.getGcmCipher(key, nonce,false);
        };
    }
}
