package io.crowds.lib.boringtun;

import io.crowds.util.SimpleNativeLibLoader;

import java.util.concurrent.atomic.AtomicInteger;

public class Wg {

    private final static AtomicInteger INDEX=new AtomicInteger(1);


    static {
        SimpleNativeLibLoader.loadLib("boringtun", Wg.class.getClassLoader());
    }


    public static int nextIndex(){
        return INDEX.getAndIncrement();
    }



}
