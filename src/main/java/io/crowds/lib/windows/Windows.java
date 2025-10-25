package io.crowds.lib.windows;

import io.crowds.util.Native;
import top.dreamlike.panama.generator.proxy.ErrorNo;
import top.dreamlike.panama.generator.proxy.MemoryLifetimeScope;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.NetworkInterface;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Objects;

public class Windows {

    private static final int IPPROTO_IP = 0;
    private static final int IP_UNICAST_IF = 31;

    static {
        System.load(Path.of(System.getenv("SystemRoot"), "System32","ws2_32.dll").toFile().getAbsolutePath());
    }

    public static final WinFFI INSTANCE = Native.nativeGenerate(WinFFI.class);
//    private static final MethodHandle setSockOpt;
//    static {
//        MethodHandle setSockOptMh=null;
//        if (PlatformDependent.isWindows()) {
//            System.load(Path.of(System.getenv("SystemRoot"), "System32","ws2_32.dll").toFile().getAbsolutePath());
//            Linker linker = Linker.nativeLinker();
////            SymbolLookup symbolLookup = SymbolLookup.libraryLookup(Path.of(System.getenv("SystemRoot"), "System32","ws2_32.dll"),Arena.global());
//            SymbolLookup symbolLookup = SymbolLookup.loaderLookup();
//            var func = symbolLookup.find("setsockopt").orElse(null);
//            if (func!=null){
//                setSockOptMh = linker.downcallHandle(func,
//                        FunctionDescriptor.of(ValueLayout.JAVA_INT,
//                                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
//                        Linker.Option.captureCallState("errno"));
//
//            }
//        }
//        setSockOpt = setSockOptMh;
//    }

    public static void bindToDevice(int fd,int index) {
        try (Arena arena = Arena.ofConfined()) {
            MemoryLifetimeScope.of(arena).active(() -> {
                MemorySegment optVal = arena.allocate(
                        ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN));
                optVal.set(ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN), 0, index);
                int r = INSTANCE.setsockopt(fd, IPPROTO_IP, IP_UNICAST_IF, optVal);
                if (r==-1){
                    int errno = ErrorNo.error.get();
                    throw new RuntimeException("Setsockopt IP_UNICAST_IF return error code: "+errno);
                }
            });
        }
    }

    public static void bindToDevice(int fd,String dev){
        Objects.requireNonNull(dev);
        try {
            int index = NetworkInterface.networkInterfaces()
                                        .filter(it->it.getDisplayName().equalsIgnoreCase(dev))
                                        .findFirst()
                                        .map(NetworkInterface::getIndex)
                                        .orElseThrow(()->new RuntimeException("Device index not found"));
            bindToDevice(fd, index);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

    }

}
