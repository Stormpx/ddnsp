package io.crowds.util;

import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import org.bouncycastle.crypto.modes.ChaCha20Poly1305;
import org.bouncycastle.jcajce.provider.symmetric.AES;
import org.bouncycastle.jcajce.provider.symmetric.DSTU7624;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class Crypto {
    public static Cipher getAESCipher(byte[] key, boolean encrypt) throws Exception{
        Cipher cipher = Cipher.getInstance("AES");
        SecretKeySpec key_spec = new SecretKeySpec(key, "AES");
        if (encrypt){
            cipher.init(Cipher.ENCRYPT_MODE, key_spec);
        }else{
            cipher.init(Cipher.DECRYPT_MODE, key_spec);
        }
        return cipher;
    }

    public static Cipher getAESWithoutPaddingCipher(byte[] key, boolean encrypt) throws Exception{
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        SecretKeySpec key_spec = new SecretKeySpec(key, "AES");
        if (encrypt){
            cipher.init(Cipher.ENCRYPT_MODE, key_spec);
        }else{
            cipher.init(Cipher.DECRYPT_MODE, key_spec);
        }
        return cipher;
    }


    public static Cipher getCFBCipher(byte[] key, byte[] ivByte, boolean encrypt) throws Exception {
        // unchanged
        SecretKeySpec key_spec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/CFB/NoPadding");
        IvParameterSpec iv = new IvParameterSpec (ivByte);
        // unchanged
        cipher.init(Cipher.ENCRYPT_MODE, key_spec, iv);
        if (encrypt){
            // Initialize Cipher for ENCRYPT_MODE
            cipher.init(Cipher.ENCRYPT_MODE, key_spec, iv);
        }else{
            // Initialize Cipher for DECRYPT_MODE
            cipher.init(Cipher.DECRYPT_MODE, key_spec, iv);
        }
        return cipher;
    }

    public static byte[] aes128CFBEncrypt(byte[] key,byte[] ivByte,byte[] payload) throws Exception {
        // unchanged
        Cipher cipher = getCFBCipher(key,ivByte,true);
        // changed
        return cipher.doFinal(payload);
    }

    public static byte[] aes128CFBDecrypt(byte[] key,byte[] ivByte,byte[] payload) throws Exception {
        Cipher cipher = getCFBCipher(key,ivByte,false);
        return cipher.doFinal(payload);
    }


    public static Cipher getGcmCipher(byte[] key, byte[] IV,boolean encrypt) throws Exception {
        // Get Cipher Instance
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        // Create SecretKeySpec
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        // Create GCMParameterSpec
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(16 * 8, IV);
        if (encrypt){
            // Initialize Cipher for ENCRYPT_MODE
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmParameterSpec);
        }else{
            // Initialize Cipher for DECRYPT_MODE
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec);
        }
        return cipher;
    }

    public static byte[] gcmEncrypt(byte[] plaintext, byte[] key, byte[] iv) throws Exception
    {
        // Get Cipher Instance
        Cipher cipher =  getGcmCipher(key, iv, true);
        // Perform Encryption
        byte[] cipherText = cipher.doFinal(plaintext);

        return cipherText;
    }

    public static byte[] gcmDecrypt(byte[] plaintext, byte[] key, byte[] iv) throws Exception
    {
        // Get Cipher Instance
        Cipher cipher =  getGcmCipher(key, iv, false);
        // Perform Decryption
        return cipher.doFinal(plaintext);
    }


    public static void gcmEncrypt(ByteBuffer plaintext,byte[] key, byte[] iv,ByteBuffer out) throws Exception
    {
        // Get Cipher Instance
        Cipher cipher =  getGcmCipher(key, iv, true);

        // Perform Encryption
        cipher.doFinal(plaintext,out);

    }


    public static void gcmDecrypt(ByteBuffer plaintext, byte[] key, byte[] iv,ByteBuffer out) throws Exception
    {
        // Get Cipher Instance
        Cipher cipher =  getGcmCipher(key, iv, false);

        // Perform Decryption
        cipher.doFinal(plaintext,out);
    }

    public static Cipher getChaCha20Poly1305Cipher(byte[] key,byte[] iv,boolean encrypt) throws Exception {
        Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
        SecretKeySpec key_spec = new SecretKeySpec(key, "ChaCha20");
        // IV, initialization value with nonce
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        if (encrypt) {
            cipher.init(Cipher.ENCRYPT_MODE, key_spec, ivSpec);
        }else{
            cipher.init(Cipher.DECRYPT_MODE, key_spec, ivSpec);
        }
        return cipher;
    }

    public static byte[] chaCha20Poly1305Encrypt(byte[] plaintext,byte[] key,byte[] iv) throws Exception {
        Cipher cipher = getChaCha20Poly1305Cipher(key,iv,true);
        byte[] encryptedText = cipher.doFinal(plaintext);

        return encryptedText;
    }

    public static void chaCha20Poly1305Encrypt(ByteBuffer plaintext,byte[] key,byte[] iv,ByteBuffer out) throws Exception {
        Cipher cipher = getChaCha20Poly1305Cipher(key,iv,true);

        cipher.doFinal(plaintext,out);

    }

    public static byte[] chaCha20Poly1305Decrypt(byte[] plaintext,byte[] key,byte[] iv) throws Exception {
        Cipher cipher = getChaCha20Poly1305Cipher(key,iv,false);
        byte[] decryptedText = cipher.doFinal(plaintext);
        return decryptedText;
    }

    public static void chaCha20Poly1305Decrypt(ByteBuffer plaintext,byte[] key,byte[] iv,ByteBuffer out) throws Exception {
        Cipher cipher = getChaCha20Poly1305Cipher(key,iv,false);
        cipher.doFinal(plaintext,out);

    }
}
