package io.crowds.lib.boringtun;

import io.crowds.util.SimpleNativeLibLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static io.crowds.lib.boringtun.wireguard_ffi_h.*;
import static io.crowds.lib.boringtun.wireguard_ffi_h.wireguard_read;

public class BoringTun {

    private final static AtomicInteger INDEX=new AtomicInteger(1);


    static {
        SimpleNativeLibLoader.loadLib("boringtun", BoringTun.class.getClassLoader());
    }


    public static int nextIndex(){
        return INDEX.getAndIncrement();
    }

    public static Tunnel newTunnel(int bufferSize,String privateKey,String publicKey,String perSharedKey,short keepalive){
        try (Arena session = Arena.ofConfined()){
            var tunnel = new_tunnel(
                    session.allocateUtf8String(privateKey),
                    session.allocateUtf8String(publicKey),
                    session.allocateUtf8String(perSharedKey),
                    keepalive,
                    nextIndex()
            );
            if (tunnel.address()==0){
                throw new RuntimeException("create wireGuard tunnel failed");
            }
            return new Tunnel(tunnel,bufferSize);
        }
    }

    public static void free(Tunnel tunnel){
        tunnel_free(tunnel.tunnel);
        tunnel.arena.close();
    }


    public static MemorySegment write(Tunnel tunnel,MemorySegment src){
        var result = wireguard_write(tunnel.resultAllocator,
                tunnel.tunnel,
                src, (int)src.byteSize(),
                tunnel.buffer, (int) tunnel.buffer.byteSize());
        return result;
    }

    public static MemorySegment read(Tunnel tunnel,MemorySegment src){
        var result = wireguard_read(tunnel.resultAllocator,
                tunnel.tunnel,
                src, (int)src.byteSize(),
                tunnel.buffer, (int) tunnel.buffer.byteSize());
        return result;
    }

    public static MemorySegment tick(Tunnel tunnel){
        return wireguard_tick(tunnel.resultAllocator, tunnel.tunnel, tunnel.buffer, (int) tunnel.getBuffer().byteSize());
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
            this.resultAllocator = SegmentAllocator.prefixAllocator(arena.allocate(wireguard_result.$LAYOUT()));
        }

        public MemorySegment getBuffer() {
            return buffer;
        }
    }
}
