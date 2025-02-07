package io.crowds;

import io.netty.util.internal.PlatformDependent;

public class Platform {

    public static boolean isLinux(){
        String name = PlatformDependent.normalizedOs();
        return "linux".equals(name);
    }

    public static boolean isWindows(){
        return PlatformDependent.isWindows();
    }

    public static boolean isOsx(){
        return PlatformDependent.isOsx();
    }

}
