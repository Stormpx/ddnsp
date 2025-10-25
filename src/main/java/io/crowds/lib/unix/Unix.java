package io.crowds.lib.unix;

import io.crowds.util.Native;
import top.dreamlike.panama.generator.annotation.NativeFunction;
import top.dreamlike.panama.generator.proxy.ErrorNo;
import top.dreamlike.panama.generator.proxy.MemoryLifetimeScope;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

public interface Unix {
    Unix INSTANCE = Native.nativeGenerate(Unix.class);
    int SOL_SOCKET = 1;
    int SO_BINDTODEVICE = 25;

    int PROT_READ= 	0x1;
    int PROT_WRITE= 	0x2;
    int PROT_EXEC= 	0x4;
    int PROT_NONE= 	0x0;
    int MAP_SHARED= 	0x01;
    int MAP_PRIVATE= 	0x02;
    int MAP_ANONYMOUS = 0x20;
    int MAP_SHARED_VALIDATE= 	0x03;
    int MAP_TYPE= 	0x0f;
    int MAP_FIXED= 	0x10;
    int MAP_FILE= 	0;

    /**
     * void *mmap (void *__addr, size_t __len, int __prot, int __flags, int __fd, __off_t __offset)
     * @return
     */
    @NativeFunction(needErrorNo = true)
    MemorySegment mmap(MemorySegment addr, long len, int prot, int flags, int fd, long offset);

    /**
     * int munmap (void *__addr, size_t __len)
     * @return
     */
    @NativeFunction(fast = true)
    int munmap(MemorySegment addr, long len);

    default int munmap(MemorySegment addr){
        return munmap(addr,addr.byteSize());
    }


    /**
     * int setsockopt(int sockfd, int level, int optname,const void optval[.optlen],socklen_t optlen);
     * setSockOpt = linker.downcallHandle(symbolLookup.find("setsockopt").orElse(null),
     *                 FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,
     *                         ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
     */
    @NativeFunction(needErrorNo = true)
    int setsockopt(int sockfd, int level, int optname, MemorySegment optval, long optlen);


    default void bindToDevice(int fd,String dev){
        Objects.requireNonNull(dev);
        try (Arena arena = Arena.ofConfined()){
            MemoryLifetimeScope.of(arena).active(()->{
                MemorySegment devname = arena.allocateFrom(dev);
                int r = setsockopt(fd,SOL_SOCKET,SO_BINDTODEVICE,devname,devname.byteSize()-1);
                System.out.println(r);
                if (r==-1){
                    throw new RuntimeException(strError(ErrorNo.error.get()));
                }
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }


    @NativeFunction
    MemorySegment strerror(int errnum);

    default String strError(int errnum){
        return strerror(errnum).reinterpret(1024).getString(0);
    }

}
