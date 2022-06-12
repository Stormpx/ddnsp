package io.crowds.util;

import io.netty.buffer.ByteBuf;

public class Bufs {

    public static int getInt(byte[] memory, int index){
        return  (memory[index]     & 0xff) << 24 |
                (memory[index + 1] & 0xff) << 16 |
                (memory[index + 2] & 0xff) <<  8 |
                memory[index + 3] & 0xff;
    }


    public static void writeInt(byte[] memory,int index,int value){
        memory[index]= (byte) (0xff&(value>>24));
        memory[index+1]= (byte) (0xff&(value>>16));
        memory[index+2]= (byte) (0xff&(value>>8));
        memory[index+3]= (byte) (0xff&(value));
    }

    public static void writeIntLE(byte[] memory,int index,int value){
        memory[index]     = (byte) value;
        memory[index + 1] = (byte) (value >>> 8);
        memory[index + 2] = (byte) (value >>> 16);
        memory[index + 3] = (byte) (value >>> 24);
    }

    public static int readUnsignedShort(byte[] memory,int index){
        return ((memory[index ] & 0xff) <<  8 |
                memory[index + 1] & 0xff);
    }

    public static short readShort(byte[] memory,int index){
        return (short) ((memory[index ] & 0xff) <<  8 |
                        memory[index + 1] & 0xff);
    }

    public static void writeShort(byte[] memory,int index,int value){
        memory[index]= (byte) (0xff&(value>>8));
        memory[index+1]= (byte) (0xff&(value));
    }

    public static void writeShortLE(byte[] memory,int index,int value){
        memory[index]= (byte) (0xff&(value));
        memory[index+1]= (byte) (0xff&(value>>8));
    }


    public static long readLong(byte[] memory,int index){
        return  ((long) memory[index]     & 0xff) << 56 |
                ((long) memory[index + 1] & 0xff) << 48 |
                ((long) memory[index + 2] & 0xff) << 40 |
                ((long) memory[index + 3] & 0xff) << 32 |
                ((long) memory[index + 4] & 0xff) << 24 |
                ((long) memory[index + 5] & 0xff) << 16 |
                ((long) memory[index + 6] & 0xff) <<  8 |
                (long) memory[index + 7] & 0xff;
    }

    public static void writeLong(byte[] memory,int index,long value){
        memory[index]     = (byte) (value >>> 56);
        memory[index + 1] = (byte) (value >>> 48);
        memory[index + 2] = (byte) (value >>> 40);
        memory[index + 3] = (byte) (value >>> 32);
        memory[index + 4] = (byte) (value >>> 24);
        memory[index + 5] = (byte) (value >>> 16);
        memory[index + 6] = (byte) (value >>> 8);
        memory[index + 7] = (byte) value;

    }

}
// for (int i = 4; i > 0; i--) {
//         int k=cmdBytes.length-i;
//         cmdBytes[k]= (byte) (f>>((i-1)*8));
//         }