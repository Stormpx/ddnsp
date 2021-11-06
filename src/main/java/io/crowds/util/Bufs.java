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
}
// for (int i = 4; i > 0; i--) {
//         int k=cmdBytes.length-i;
//         cmdBytes[k]= (byte) (f>>((i-1)*8));
//         }