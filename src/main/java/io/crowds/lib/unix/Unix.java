package io.crowds.lib.unix;


import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public class Unix {
    private static final int SOL_SOCKET = 1;

    private static final int SO_BINDTODEVICE = 25;

    private static final Linker LINKER = Linker.nativeLinker();

    private static final MethodHandle setSockOpt;
    static final MethodHandle error;
    static final MethodHandle strerror;

    static {
        SymbolLookup symbolLookup = LINKER.defaultLookup();
        setSockOpt = LINKER.downcallHandle(symbolLookup.find("setsockopt").orElse(null),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS.withTargetLayout(MemoryLayout.sequenceLayout(JAVA_BYTE)),ValueLayout.JAVA_LONG));
        error = LINKER.downcallHandle(symbolLookup.find("__errno_location").orElse(null),FunctionDescriptor.of(ValueLayout.ADDRESS));
        strerror = LINKER.downcallHandle(symbolLookup.find("strerror").orElse(null),FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    }

    public static int errno() {
        try {
            MemorySegment addr = (MemorySegment) error.invokeExact();
            return addr.reinterpret(Long.MAX_VALUE).get(ValueLayout.JAVA_INT, 0);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static String strerror(int errno){
        try {
            MemorySegment addr = (MemorySegment) strerror.invokeExact(errno);
            return addr.reinterpret(Long.MAX_VALUE).getUtf8String(0);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

    }


    public static void bindToDevice(int fd,String dev){
        Objects.requireNonNull(dev);
        try (Arena arena = Arena.ofConfined()){
            MemorySegment devname = arena.allocateUtf8String(dev);
            int r = (int) setSockOpt.invokeExact(fd,SOL_SOCKET,SO_BINDTODEVICE,devname,devname.byteSize()-1);
            if (r==-1){
                throw new RuntimeException(strerror(errno()));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

}
