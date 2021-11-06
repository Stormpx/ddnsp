package io.crowds.util;

import java.util.concurrent.ThreadLocalRandom;

public class Rands {


    public static byte[] genBytes(int len){
        byte[] bytes=new byte[len];
        ThreadLocalRandom.current().nextBytes(bytes);
        return bytes;
    }

    public static void genBytes(byte[] bytes,int off,int len){
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < len; i++) {
            bytes[i+off]= (byte) random.nextInt();
        }
    }
    public static byte nextByte(){
        return (byte) ThreadLocalRandom.current().nextInt(0,256);
    }
}
