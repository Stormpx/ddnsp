package io.crowds.util;

import org.bouncycastle.crypto.digests.KeccakDigest;
import org.bouncycastle.crypto.digests.SHAKEDigest;
import org.bouncycastle.jcajce.provider.asymmetric.rsa.PSSSignatureSpi;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jcajce.provider.digest.SHA3;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hash {

    public static String md5AsHex(byte[] bytes){
        try {

            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(bytes);
            return String.format("%032x", new BigInteger(1, md5.digest()));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] md5(byte[] bytes){
        try {

            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(bytes);
            return md5.digest();
        } catch (NoSuchAlgorithmException ignored) {
            throw new RuntimeException(ignored);
        }
    }

    public static String sha1(byte[] bytes){
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA1");
            byte[] digest = sha1.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (int i=0; i < digest.length; i++) {
                sb.append(Integer.toString(( digest[i] & 0xff ) + 0x100, 16).substring( 1 ));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String sha256(byte[] bytes){
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (int i=0; i < digest.length; i++) {
                sb.append(Integer.toString(( digest[i] & 0xff ) + 0x100, 16).substring( 1 ));
            }
            return sb.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }
    public static byte[] hmac(byte[] msg, byte[] k, String algo) {
        try {
            SecretKeySpec key = new SecretKeySpec(k, algo);
            Mac mac = Mac.getInstance(algo);
            mac.init(key);
            byte[] bytes = mac.doFinal(msg);

            return bytes;
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }


    public static int fnv1a32(byte[] bytes,int length){
        int hash=0x811c9dc5;
        for (int i = 0; i < length; i++) {
            byte b=bytes[i];
            hash ^= (b&0xff);
            hash *= 16777619;
        }
        return hash;
    }

    public static int fnv1a32(byte[] bytes,int index,int length){
        int hash=0x811c9dc5;
        int len=index+length;
        for (int i = index; i < len; i++) {
            byte b=bytes[i];
            hash ^= (b&0xff);
            hash *= 16777619;
        }
        return hash;
    }


}