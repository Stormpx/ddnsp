package io.crowds.proxy.transport.proxy.shadowsocks;

import io.crowds.util.Crypto;
import io.crowds.util.Hash;
import io.crowds.util.Rands;

public class AEAD {

    public static AEADCodec.TcpCodec tcp(ShadowsocksOption option,SaltPool saltPool){
        return switch (option.getCipher()){
            case CHACHA20_IETF_POLY1305,CHACHA20_IETF_POLY1305_2022,AES_128_GCM,AES_192_GCM,AES_256_GCM ->new AEADCodec.TcpCodec(option);
            case AES_128_GCM_2022,AES_256_GCM_2022 -> new AEAD2022Codec.TCP2022Codec(option,saltPool);
        };
    }

    public static AEADCodec.UdpCodec udp(ShadowsocksOption option)  {
        return switch (option.getCipher()){
            case CHACHA20_IETF_POLY1305,CHACHA20_IETF_POLY1305_2022,AES_128_GCM,AES_192_GCM,AES_256_GCM ->new AEADCodec.UdpCodec(option);
            case AES_128_GCM_2022,AES_256_GCM_2022 -> new AEAD2022Codec.Udp2022Codec(option);
        };
    }


    public static byte[] genSalt(CipherAlgo cipherAlgo){
        return Rands.genBytes(cipherAlgo.getSaltSize());
    }

    public static byte[] genSubKey(CipherAlgo cipherAlgo, byte[] masterKey, byte[] salt){
        return switch (cipherAlgo){
            case CHACHA20_IETF_POLY1305,CHACHA20_IETF_POLY1305_2022,AES_128_GCM,AES_192_GCM,AES_256_GCM ->Hash.hkdfSHA1(masterKey,salt,"ss-subkey".getBytes(), cipherAlgo.getKeySize());
            case AES_128_GCM_2022,AES_256_GCM_2022 -> {
                byte[] material = new byte[masterKey.length+salt.length];
                System.arraycopy(masterKey,0,material,0,masterKey.length);
                System.arraycopy(salt,0,material,masterKey.length,salt.length);
                yield Hash.kdfBlake3("shadowsocks 2022 session subkey".getBytes(),material, cipherAlgo.getKeySize());
            }
        };
    }
//    2d87dd17d29af53769847253298d3390607c52a9cfa6f56b0e73589e620bbd109376a67423cdd1ab972a87e9fb1b9ba70a363b6da18fc3daf4b566ed6c82bc4e
//    2d87dd17d29af53769847253298d3390607c52a9cfa6f56b0e73589e620bbd10f77efa6baef8db771d77569bf7bd9af3b7bd7dbd5bf5b6bbd1adfaddbe9d6b5f1f73775a7f86f9eba79de9cf366dce1e
    public static javax.crypto.Cipher getEncryptCipher(CipherAlgo cipherAlgo, byte[] key, byte[] nonce) throws Exception {
        return switch (cipherAlgo) {
            case CHACHA20_IETF_POLY1305,CHACHA20_IETF_POLY1305_2022 -> Crypto.getChaCha20Poly1305Cipher( key, nonce,true);
            case AES_256_GCM, AES_192_GCM, AES_128_GCM,AES_128_GCM_2022,AES_256_GCM_2022 -> Crypto.getGcmCipher(key, nonce,true);
        };
    }

    public static javax.crypto.Cipher getDecryptCipher(CipherAlgo cipherAlgo, byte[] key, byte[] nonce) throws Exception {
        return switch (cipherAlgo) {
            case CHACHA20_IETF_POLY1305,CHACHA20_IETF_POLY1305_2022 -> Crypto.getChaCha20Poly1305Cipher( key, nonce,false);
            case AES_256_GCM, AES_192_GCM, AES_128_GCM,AES_128_GCM_2022,AES_256_GCM_2022 -> Crypto.getGcmCipher(key, nonce,false);
        };
    }
}
