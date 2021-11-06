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
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class Crypto {

    public static byte[] aes128CFBEncrypt(byte[] key,byte[] ivByte,byte[] payload) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, ShortBufferException, IllegalBlockSizeException {
        // unchanged
        SecretKeySpec key_spec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/CFB/NoPadding");
        // changed
        int block_size = cipher.getBlockSize();
        IvParameterSpec iv = new IvParameterSpec (ivByte);
        // unchanged
        cipher.init(Cipher.ENCRYPT_MODE, key_spec, iv);
        // changed
        return cipher.doFinal(payload);
    }

    public static byte[] aes128CFBDecrypt(byte[] key,byte[] ivByte,byte[] payload) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        // unchanged
        SecretKeySpec key_spec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/CFB/NoPadding");
        // changed
        int block_size = cipher.getBlockSize();
        // create random IV
        IvParameterSpec iv = new IvParameterSpec (ivByte);
        // unchanged
        cipher.init(Cipher.DECRYPT_MODE, key_spec, iv);
        // changed
        return cipher.doFinal(payload);
    }



    public static byte[] gcmEncrypt(byte[] plaintext, byte[] key, byte[] IV) throws Exception
    {
        // Get Cipher Instance
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        // Create SecretKeySpec
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");

        // Create GCMParameterSpec
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(16 * 8, IV);

        // Initialize Cipher for ENCRYPT_MODE
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmParameterSpec);

        // Perform Encryption
        byte[] cipherText = cipher.doFinal(plaintext);

        return cipherText;
    }

    public static byte[] gcmDecrypt(byte[] cipherText, byte[] key, byte[] IV) throws Exception
    {
        // Get Cipher Instance
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        // Create SecretKeySpec
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");

        // Create GCMParameterSpec
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(16 * 8, IV);

        // Initialize Cipher for DECRYPT_MODE
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec);
        // Perform Decryption
        byte[] decryptedText = cipher.doFinal(cipherText);
        return decryptedText;
    }

    public static byte[] chaCha20Poly1305Encrypt(byte[] plaintext,byte[] key,byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
        SecretKeySpec key_spec = new SecretKeySpec(key, "ChaCha20");
        // IV, initialization value with nonce
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        cipher.init(Cipher.ENCRYPT_MODE, key_spec, ivSpec);

        byte[] encryptedText = cipher.doFinal(plaintext);

        return encryptedText;
    }

    public static byte[] chaCha20Poly1305Decrypt(byte[] plaintext,byte[] key,byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
        SecretKeySpec key_spec = new SecretKeySpec(key, "ChaCha20");
        // IV, initialization value with nonce
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        cipher.init(Cipher.DECRYPT_MODE, key_spec, ivSpec);

        byte[] decryptedText = cipher.doFinal(plaintext);

        return decryptedText;
    }
}
