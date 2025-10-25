package io.crowds.lib.boringtun;

import io.crowds.util.Native;
import io.crowds.util.SimpleNativeLibLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.dreamlike.panama.generator.proxy.MemoryLifetimeScope;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicInteger;

public class BoringTun {
    private static final Logger logger = LoggerFactory.getLogger(BoringTun.class);

    private final static AtomicInteger INDEX=new AtomicInteger(1);


    static {
        SimpleNativeLibLoader.loadLib("boringtun", BoringTun.class.getClassLoader());

        try {
            var logFn = MethodHandles.lookup().findStatic(BoringTun.class,"log",
                    MethodType.methodType(void.class, MemorySegment.class));
            var fp = Linker.nativeLinker().upcallStub(logFn, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS),Arena.global());
            WireguardFFI.INSTANCE.set_logging_function(fp);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

    }

    static void log(MemorySegment str){
        String s = str.reinterpret(1024).getString(0);
        logger.debug(s);
    }

    public static int nextIndex(){
        return INDEX.getAndIncrement();
    }

    public static Tunnel newTunnel(int bufferSize,String privateKey,String publicKey,String perSharedKey,short keepalive){
        try (Arena session = Arena.ofConfined()){
            return MemoryLifetimeScope.of(session)
                    .active(()->{
                        var tunnel = WireguardFFI.INSTANCE.new_tunnel(
                                privateKey,
                                publicKey,
                                perSharedKey,
                                keepalive,
                                nextIndex()
                        );
                        if (tunnel.address()==0){
                            throw new RuntimeException("Create wireGuard tunnel failed");
                        }
                        return new Tunnel(tunnel,bufferSize);
                    });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void free(Tunnel tunnel){
        WireguardFFI.INSTANCE.tunnel_free(tunnel.tunnel);
        tunnel.arena.close();
    }


    public static WireguardResult write(Tunnel tunnel,MemorySegment src) throws Exception {
        return MemoryLifetimeScope.of(tunnel.resultAllocator)
                .active(()->{
                    var result = WireguardFFI.INSTANCE.wireguard_write(
                            tunnel.tunnel,
                            src, (int)src.byteSize(),
                            tunnel.buffer, (int) tunnel.buffer.byteSize());
                    return result;
                });

    }

    public static WireguardResult read(Tunnel tunnel,MemorySegment src) throws Exception {
        return MemoryLifetimeScope.of(tunnel.resultAllocator)
                .active(()->{
                    var result = WireguardFFI.INSTANCE.wireguard_read(
                            tunnel.tunnel,
                            src, (int)src.byteSize(),
                            tunnel.buffer, (int) tunnel.buffer.byteSize());
                    return result;
                });

    }

    public static WireguardResult tick(Tunnel tunnel) throws Exception {
        return MemoryLifetimeScope.of(tunnel.resultAllocator)
                .active(()->{
                    var result = WireguardFFI.INSTANCE.wireguard_tick(
                            tunnel.tunnel,
                            tunnel.buffer, (int) tunnel.buffer.byteSize());
                    return result;
                });

    }


    public static class Tunnel{
        private final MemorySegment tunnel;
        private final Arena arena;
        private final SegmentAllocator resultAllocator;
        private final MemorySegment buffer;

        public Tunnel(MemorySegment tunnel, int bufferSize) {
            this.tunnel = tunnel;
            this.arena =Arena.ofShared();
            this.buffer = this.arena.allocate(bufferSize);
            this.resultAllocator = SegmentAllocator.prefixAllocator(arena.allocate(Native.getLayout(WireguardResult.class)));
        }

        public MemorySegment getBuffer() {
            return buffer;
        }
    }
}
