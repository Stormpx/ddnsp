package io.crowds;

import io.crowds.util.SemVer;
import io.netty.util.internal.PlatformDependent;

public class Platform {

    private static final StableValue<SemVer> LINUX_SEM_VER = StableValue.of();

    public static boolean isLinux(){
        String name = PlatformDependent.normalizedOs();
        return "linux".equals(name);
    }

    public static SemVer linuxVer(){
        if (isLinux()&&!LINUX_SEM_VER.isSet()){
            LINUX_SEM_VER.trySet(SemVer.parse(System.getProperty("os.version")));
        }
        return LINUX_SEM_VER.orElse(null);
    }

    public static boolean isWindows(){
        return PlatformDependent.isWindows();
    }

    public static boolean isOsx(){
        return PlatformDependent.isOsx();
    }

}
