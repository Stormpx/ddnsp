package io.crowds.util;

public class Ints {

    public static boolean isAvailablePort(Integer port){
        return port!=null&&port>=0&&port<=65535;
    }


    public static long diff(long a,long b){
        return Math.abs(a-b);
    }
}
