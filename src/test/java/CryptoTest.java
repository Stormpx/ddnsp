import io.crowds.proxy.transport.proxy.shadowsocks.AEAD;
import io.crowds.proxy.transport.proxy.shadowsocks.CipherAlgo;
import io.crowds.util.Crypto;
import io.crowds.util.Hash;
import io.crowds.util.Rands;
import io.netty.buffer.ByteBufUtil;
import org.junit.Assert;
import org.junit.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

public class CryptoTest {

    public static void main(String[] args) throws Exception {
        byte[] key=new byte[32];
        byte[] iv=new byte[12];
        ThreadLocalRandom.current().nextBytes(iv);
        ThreadLocalRandom.current().nextBytes(key);
//        iv=HexFormat.of().parseHex("9376a67423cdd1ab972a87e9fb1b9ba70a363b6da18fc3daf4b566ed6c82bc4e");
//
//        byte[] key = AEAD.genSubKey(CipherAlgo.AES_256_GCM_2022, Base64.getDecoder().decode("LYfdF9Ka9TdphHJTKY0zkGB8UqnPpvVrDnNYnmILvRA="), iv);
//
//        System.out.println(HexFormat.of().formatHex(key));



    }
    @Test
    public void test(){
        Assert.assertEquals(
                "23097d223405d8228642a477bda255b32aadbce4bda0b3f7e36c9da7",
                Hash.sha224Hex("abc".getBytes(StandardCharsets.UTF_8)));
        Assert.assertEquals(
                "75388b16512776cc5dba5da1fd890150b0c6455cb4f58b1952522525",
                Hash.sha224Hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void gcm() throws Exception {
        byte[] key128= Rands.genBytes(16);
        byte[] key192= Rands.genBytes(24);
        byte[] key256= Rands.genBytes(32);
        byte[] iv=new byte[12];
        ThreadLocalRandom.current().nextBytes(iv);

        byte[] plain = "dhwioadh12315616iowadhwioadhwa".getBytes(StandardCharsets.UTF_8);
//        byte[] plain = new byte[0];

        byte[] encrypt128 = Crypto.gcmEncrypt(plain, key128, iv);
        byte[] encrypt192 = Crypto.gcmEncrypt(plain, key192, iv);
        byte[] encrypt256 = Crypto.gcmEncrypt(plain, key256, iv);
        System.out.println(ByteBufUtil.hexDump(encrypt128));
        System.out.println(ByteBufUtil.hexDump(encrypt192));
        System.out.println(ByteBufUtil.hexDump(encrypt256));
        System.out.println(encrypt128.length);
        byte[] decrypt = Crypto.gcmDecrypt(encrypt128, key128, iv);
        System.out.println(decrypt.length);
        System.out.println(plain.length);

    }

    @Test
    public void chacha20Poly1305() throws Exception {
        byte[] key=new byte[16];
        byte[] iv=new byte[12];
        ThreadLocalRandom.current().nextBytes(key);
        ThreadLocalRandom.current().nextBytes(iv);

        byte[] plain = "dhwioadh12315616iowadhwioadhwa".getBytes(StandardCharsets.UTF_8);
//        byte[] plain = new byte[0];

        byte[] md5Key = Hash.md5(key);
        byte[] md5md5Key = Hash.md5(md5Key);
        byte[] chchaKey = new byte[32];
        System.arraycopy(md5Key,0,chchaKey,0,16);
        System.arraycopy(md5md5Key,0,chchaKey,16,16);


        byte[] encrypt = Crypto.chaCha20Poly1305Encrypt(plain, chchaKey, iv);
        System.out.println(plain.length);
        System.out.println(encrypt.length);
        byte[] decrypt = Crypto.chaCha20Poly1305Decrypt(encrypt, chchaKey, iv);
        System.out.println(decrypt.length);

        Assert.assertArrayEquals(plain,decrypt);
    }



}
