package io.crowds.lib.unix;

import io.crowds.util.Native;
import top.dreamlike.panama.generator.annotation.NativeFunction;
import top.dreamlike.panama.generator.proxy.ErrorNo;
import top.dreamlike.panama.generator.proxy.MemoryLifetimeScope;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

public interface Unix {
    Unix INSTANCE = Native.nativeGenerate(Unix.class);
    int O_NONBLOCK = 00004000;
    int O_DIRECT = 00040000;

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

    int MSG_DONTWAIT = 0x40;

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


    @NativeFunction(fast = true)
    int getpagesize();

    /**
     * unsigned int if_nametoindex(const char *ifname);
     */
    @NativeFunction(fast = true)
    int if_nametoindex(String ifname);

    /**
     *  int pipe2(int pipefd[2], int flags);
     */
    @NativeFunction(needErrorNo = true)
    int pipe2(MemorySegment pipefd,int flags);

    default int[] pipe(int flags){
        try (Arena arena = Arena.ofConfined()){
            MemorySegment pipefd = arena.allocate(ValueLayout.JAVA_INT,2);
            MemoryLifetimeScope.of(arena).active(()->{
                int r = pipe2(pipefd,flags);
                if (r==-1){
                    throw new RuntimeException(strError(-ErrorNo.getCapturedError().errno()));
                }
            });
            int[] fds = new int[2];
            fds[0] = pipefd.getAtIndex(ValueLayout.JAVA_INT,0);
            fds[1] = pipefd.getAtIndex(ValueLayout.JAVA_INT,1);
            return fds;
        }
    }

    /**
     * ssize_t sendto(int socket, const void *message, size_t length,
     *            int flags, const struct sockaddr *dest_addr,
     *            socklen_t dest_len);
     */
    @NativeFunction(needErrorNo = true)
    long sendto(int socket, MemorySegment message, long length,int flags,MemorySegment dest_addr,int dest_len);

    /**
     * ssize_t recvfrom(int socket, void *restrict buffer, size_t length,
     *            int flags, struct sockaddr *restrict address,
     *            socklen_t *restrict address_len);
     */
    @NativeFunction(needErrorNo = true)
    long recvfrom(int socket, MemorySegment buffer, long length,int flags,MemorySegment address,MemorySegment address_len);


    /**
     * ssize_t write(int fd, const void buf[.count], size_t count);
     */
    @NativeFunction(needErrorNo = true)
    long write(int fd, MemorySegment buf, long count);

    /**
     * ssize_t read(int fd, void buf[.count], size_t count);
     */
    @NativeFunction(needErrorNo = true)
    long read(int fd, MemorySegment buf, long count);

    /**
     * int close(int fd);
     */
    @NativeFunction(needErrorNo = true)
    int close(int fd);

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
                if (r==-1){
                    throw new RuntimeException(strError(ErrorNo.getCapturedError().errno()));
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
