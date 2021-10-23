import io.crowds.util.Crypto;
import io.crowds.util.Hash;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.Assert;
import org.junit.Test;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class CryptoTest {

    public static void main(String[] args) throws NoSuchPaddingException, ShortBufferException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {

        byte[] key=new byte[16];
        byte[] iv=new byte[16];
        ThreadLocalRandom.current().nextBytes(key);
        ThreadLocalRandom.current().nextBytes(iv);

//        byte[] plain = "dhwioadh12315616iowadhwioadhwa".getBytes(StandardCharsets.UTF_8);
        byte[] plain = new byte[0];
        System.out.println(plain.length);
        byte[] bytes = Crypto.aes128CFBEncrypt(key, iv, plain);

        System.out.println(bytes.length);

        byte[] r = Crypto.aes128CFBDecrypt(key, iv, bytes);
        System.out.println(new String(r,StandardCharsets.UTF_8));
        System.out.println(Arrays.equals(plain,r));

//        byte[] bytes1=new byte[bytes.length/2];
//        System.arraycopy(bytes,0,bytes1,0,bytes1.length);
//        byte[] r1 = Crypto.aes128CFBDecrypt(key, iv, bytes1);
//        System.out.println(new String(r1,StandardCharsets.UTF_8));
//
//        byte[] bytes2=new byte[bytes.length/2];
//        System.arraycopy(bytes,15,bytes2,0,bytes2.length);
//        byte[] r2 = Crypto.aes128CFBDecrypt(key, iv, bytes2);
//        System.out.println(new String(r2,StandardCharsets.UTF_8));




    }

    @Test
    public void gcm() throws Exception {
        byte[] key=new byte[16];
        byte[] iv=new byte[12];
        ThreadLocalRandom.current().nextBytes(key);
        ThreadLocalRandom.current().nextBytes(iv);

//        byte[] plain = "dhwioadh12315616iowadhwioadhwa".getBytes(StandardCharsets.UTF_8);
        byte[] plain = new byte[0];

        byte[] encrypt = Crypto.gcmEncrypt(plain, key, iv);
        System.out.println(encrypt.length);
        byte[] decrypt = Crypto.gcmDecrypt(encrypt, key, iv);
        System.out.println(decrypt.length);
        System.out.println(plain.length);

    }

    @Test
    public void chacha20Poly1305() throws Exception {
        byte[] key=new byte[16];
        byte[] iv=new byte[12];
        ThreadLocalRandom.current().nextBytes(key);
        ThreadLocalRandom.current().nextBytes(iv);

//        byte[] plain = "dhwioadh12315616iowadhwioadhwa".getBytes(StandardCharsets.UTF_8);
        byte[] plain = new byte[0];

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
