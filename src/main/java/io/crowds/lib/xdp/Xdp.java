package io.crowds.lib.xdp;

import io.crowds.Platform;
import io.crowds.lib.unix.Unix;
import io.crowds.lib.xdp.ffi.*;
import io.crowds.util.Native;
import io.crowds.util.SimpleNativeLibLoader;
import top.dreamlike.panama.generator.proxy.ErrorNo;
import top.dreamlike.panama.generator.proxy.MemoryLifetimeScope;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;

public class Xdp {
    private final static Throwable UNAVAILABLE_CAUSE;
    static {
        Throwable unavailableCause = null;
        if (!Platform.isLinux()){
            unavailableCause = new RuntimeException("XDP is only supported on Linux");
        }else{
            try {
                SimpleNativeLibLoader.loadLib("bpf", Xdp.class.getClassLoader());
                SimpleNativeLibLoader.loadLib("xdp", Xdp.class.getClassLoader());
            } catch (Exception e) {
                unavailableCause = e;
            }
        }

        UNAVAILABLE_CAUSE = unavailableCause;
    }

    private static void ensureAvailable(){
        if (UNAVAILABLE_CAUSE!=null){
            throw (Error) new UnsatisfiedLinkError().initCause(UNAVAILABLE_CAUSE);
        }
    }

    private static int nextPowerOfTwo(int value){
        int offset = Integer.highestOneBit(value);
        if (offset != Integer.lowestOneBit(value)){
            value = Math.toIntExact(Math.min(1L << offset-1, Integer.MAX_VALUE));
        }
        return value;
    }

    public static Umem createUmem(int chunks, int chunkSize, int fillSize, int compSize){
        ensureAvailable();

        int pageSize = Unix.INSTANCE.getpagesize();
        chunkSize = Math.max(2048,chunkSize);
        chunkSize = nextPowerOfTwo(chunkSize);
        chunkSize = Math.min(pageSize,chunkSize);
        chunks = Math.max(1024,chunks);
        fillSize = nextPowerOfTwo(fillSize);
        compSize = nextPowerOfTwo(compSize);

        Arena arena = Arena.global();
        int finalChunks = chunks;
        int finalChunkSize = chunkSize;
        int finalFillSize = fillSize;
        int finalCompSize = compSize;
        try {

            return MemoryLifetimeScope.of(arena).active(()->{
                long umemSize = (long) finalChunks * finalChunkSize;
                var buffer = Unix.INSTANCE.mmap(MemorySegment.NULL, umemSize, Unix.PROT_READ | Unix.PROT_WRITE,
                        Unix.MAP_PRIVATE | Unix.MAP_ANONYMOUS, -1, 0);
                if (buffer.address()==-1) {
                    int err = ErrorNo.getCapturedError().errno();
                    throw new RuntimeException("mmap umem failed "+Unix.INSTANCE.strError(err));
                }

                buffer = buffer.reinterpret(umemSize);
                XskUmemConfig config = Native.structAlloc(arena, XskUmemConfig.class);
                XskRing fill = Native.structAlloc(arena, XskRing.class);
                XskRing comp = Native.structAlloc(arena, XskRing.class);

                config.setFill_size(finalFillSize);
                config.setComp_size(finalCompSize);
                config.setFrame_size(finalChunkSize);
                config.setFrame_headroom(Xsk.XSK_UMEM__DEFAULT_FRAME_HEADROOM);
                config.setFlags(0);

                MemorySegment umem = arena.allocate(ValueLayout.ADDRESS);
                int ret = Xsk.INSTANCE.xsk_umem__create(umem, buffer, buffer.byteSize(), fill, comp,
                        config);
                if (ret != 0) {
                    throw new RuntimeException("create umem failed "+ret);
                }
                umem = umem.get(ValueLayout.ADDRESS, 0);
                return new Umem(umem, buffer,finalChunks, finalChunkSize,fill,comp);
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public static XdpSocket createXsk(String ifname, int queueId, Umem umem, int rxSize, int txSize){
        Arena arena = Arena.global();
        MemorySegment ifnamePtr = arena.allocateFrom(ifname);
        XskSocketConfig cfg = Native.structAlloc(arena, XskSocketConfig.class);
        cfg.setRx_size(nextPowerOfTwo(rxSize));
        cfg.setTx_size(nextPowerOfTwo(txSize));
        cfg.setXdp_flags(0);
        cfg.setBind_flags((short) 0);
        cfg.getLibFlags().setLibxdp_flags(Xsk.XSK_LIBXDP_FLAGS__INHIBIT_PROG_LOAD);

        XskRing tx = Native.structAlloc(arena, XskRing.class);
        XskRing comp = Native.structAlloc(arena, XskRing.class);
        XskRing rx = Native.structAlloc(arena, XskRing.class);
        XskRing fill = Native.structAlloc(arena, XskRing.class);

        MemorySegment xsk = arena.allocate(ValueLayout.ADDRESS);

        int ret = Xsk.INSTANCE.xsk_socket__create_shared(xsk, ifnamePtr, queueId, umem.umem(), rx, tx, fill,comp,cfg);
        if (ret!=0){
            throw new RuntimeException("create xsk socket failed "+ Unix.INSTANCE.strError(-ret));
        }
        xsk = xsk.get(ValueLayout.ADDRESS,0);
        return new XdpSocket(xsk,umem,tx,rx,fill,comp);
    }

    private static XdpMultiProg findExistsProg(int ifIndex){
        LibXdp libXdp = LibXdp.INSTANCE;
        var prog =  libXdp.xdp_multiprog__get_from_ifindex(ifIndex);
        long err = libXdp.libxdp_get_error(prog);
        if (err!=0){
            if (-err==2){
                return null;
            }
            MemorySegment errMsg = Arena.ofAuto().allocate(1024);
            libXdp.libxdp_strerror((int) err,errMsg);
            throw new RuntimeException("Find exists prog failed: "+errMsg.getString(0));
        }
        return prog;
    }


    public static boolean isXdpProgAlreadyExists(int ifindex){
        XdpMultiProg mp = findExistsProg(ifindex);
        boolean exists = mp !=null;
        if (exists){
            LibXdp.INSTANCE.xdp_multiprog__close(mp);
        }
        return exists;
    }

    public static void unloadXdpProg(int ifindex){
        LibXdp libXdp = LibXdp.INSTANCE;
        XdpMultiProg mp = findExistsProg(ifindex);
        if (mp !=null){
            try {
                int err = libXdp.xdp_multiprog__detach(mp);
                if (err!=0){
                    throw new RuntimeException("Could not detach xdp prog from iface: "+Unix.INSTANCE.strError(-err));
                }
            }finally {
                libXdp.xdp_multiprog__close(mp);
            }
        }
    }

    private static XdpProg create(XdpProgram xdpProgram, int ifIndex){
        Arena arena = Arena.global();
        var xdp = LibXdp.INSTANCE;
        long err = xdp.libxdp_get_error(xdpProgram);
        if (err!=0){
            MemorySegment errMsg = arena.allocate(512);
            xdp.libxdp_strerror((int) err,errMsg);
            throw new RuntimeException("Load xdp prog err: "+errMsg.getString(0));
        }

        return new XdpProg(xdpProgram,ifIndex);
    }

    public static XdpProg openFile(Path path, String sectionName,int ifIndex){
        Arena arena = Arena.global();
        var xdp = LibXdp.INSTANCE;
        var xdpProgram = xdp.xdp_program__open_file(
                arena.allocateFrom(path.toString()),
                sectionName==null?MemorySegment.NULL:arena.allocateFrom(sectionName),
                MemorySegment.NULL
        );
        return create(xdpProgram,ifIndex);
    }

    public static XdpProg findFile(String filename,String sectionName,int ifIndex){
        Arena arena = Arena.global();
        var xdp = LibXdp.INSTANCE;
        var xdpProgram = xdp.xdp_program__find_file(
                arena.allocateFrom(filename),
                sectionName==null?MemorySegment.NULL:arena.allocateFrom(sectionName),
                MemorySegment.NULL
        );
        return create(xdpProgram,ifIndex);
    }

    public static <T> BpfRingBuffer newRingBuffer(int mapFd,Class<T> klass,BpfRingBuffer.RingBufferCallback<T> callback){
        try (Arena arena = Arena.ofConfined()){
            return MemoryLifetimeScope.of(arena).active(()->{
                MemorySegment entryPoint = BpfRingBuffer.genEntryPoint(klass, callback);
                var rb = LibBpf.INSTANCE.ring_buffer__new(mapFd, entryPoint, MemorySegment.NULL, MemorySegment.NULL);
                if (rb==null){
                    var errno = -ErrorNo.error.get();
                    throw new RuntimeException("Create ring buffer failed: "+Unix.INSTANCE.strError(errno));
                }
                return new BpfRingBuffer(mapFd,rb,entryPoint);
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public static BpfRingBuffer newRingBuffer(int mapFd,BpfRingBuffer.RingBufferCallback<MemorySegment> callback){
        return newRingBuffer(mapFd, MemorySegment.class,callback);
    }

}
