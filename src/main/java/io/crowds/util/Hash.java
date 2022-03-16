package io.crowds.util;

import org.bouncycastle.crypto.digests.KeccakDigest;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.digests.SHA224Digest;
import org.bouncycastle.crypto.digests.SHAKEDigest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.params.KDFParameters;
import org.bouncycastle.jcajce.provider.asymmetric.rsa.PSSSignatureSpi;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jcajce.provider.digest.SHA3;
import org.bouncycastle.util.encoders.HexEncoder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.DigestException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.CRC32;

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

    public static String sha256AsHex(byte[] bytes){
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
    public static byte[] sha256(byte[] bytes){
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return  digest.digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }
    public static void sha256(byte[] bytes,byte[] out){
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes);
            digest.digest(out,0,out.length);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        return fnv1a32(bytes,0,length);
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

    public static int fnv1a32(ByteBuffer buffer,int length){
        return fnv1a32(buffer,0,length);
    }
    public static int fnv1a32(ByteBuffer buffer,int index,int length){
        int hash=0x811c9dc5;
        int len=index+length;
        for (int i = index; i < len; i++) {
            byte b=buffer.get(i);
            hash ^= (b&0xff);
            hash *= 16777619;
        }
        return hash;
    }

    public static byte[] hkdfSHA1(byte[] key,byte[] salt,byte[] info,int len){
        var gen=new HKDFBytesGenerator(new SHA1Digest());
        gen.init(new HKDFParameters(key,salt,info));
        byte[] bytes=new byte[len];
        gen.generateBytes(bytes,0,len);
        return bytes;
    }


    public static long crc32(ByteBuffer buffer){
        CRC32 crc32 = new CRC32();
        crc32.update(buffer);
        return crc32.getValue();
    }

    public static String sha224Hex(byte[] bytes){
        SHA224Digest digest = new SHA224Digest();
        digest.update(bytes,0, bytes.length);
        byte[] b=new byte[digest.getDigestSize()];
        digest.doFinal(b,0);
        return HexFormat.of().formatHex(b);
    }
    public static byte[] sha224(byte[] bytes){
        SHA224Digest digest = new SHA224Digest();
        digest.update(bytes,0, bytes.length);
        byte[] b=new byte[digest.getDigestSize()];
        digest.doFinal(b,0);
        return b;
    }

}