package io.crowds.lib.windows;

import io.netty.util.internal.PlatformDependent;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.net.NetworkInterface;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Objects;

public class Windows {

    private static final int IPPROTO_IP = 0;
    private static final int IP_UNICAST_IF = 31;


    private static final MethodHandle setSockOpt;
    private static final MethodHandle getLastError;
    static {
        MethodHandle setSockOptMh=null;
        MethodHandle getLastErrorMh=null;
        if (PlatformDependent.isWindows()) {
            Linker linker = Linker.nativeLinker();
            SymbolLookup symbolLookup = SymbolLookup.libraryLookup(Path.of(System.getenv("SystemRoot"), "System32","ws2_32.dll"),Arena.global());
            var func = symbolLookup.find("setsockopt").orElse(null);
            if (func!=null){
                setSockOptMh = linker.downcallHandle(func,
                        FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

                var errorFunc = symbolLookup.find("WSAGetLastError").orElse(null);
                if (errorFunc!=null) {
                    getLastErrorMh = linker.downcallHandle(errorFunc, FunctionDescriptor.of(ValueLayout.JAVA_INT));
                }
            }
        }
        setSockOpt = setSockOptMh;
        getLastError = getLastErrorMh;
    }


    public static void bindToDevice(int fd,String dev){
        Objects.requireNonNull(dev);
        try (Arena arena = Arena.ofConfined()){
            int index = NetworkInterface.networkInterfaces()
                            .filter(it->it.getDisplayName().equalsIgnoreCase(dev))
                            .findFirst()
                            .map(NetworkInterface::getIndex)
                            .orElseThrow(()->new RuntimeException("Device index not found"));
            MemorySegment optVal = arena.allocate(ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN), index);
            int r = (int) setSockOpt.invoke(fd,IPPROTO_IP,IP_UNICAST_IF,optVal,(int)optVal.byteSize());
            if (r==-1){
                int error = (int) getLastError.invoke();
                throw new RuntimeException("Setsockopt IP_UNICAST_IF return error code: "+error);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

}
