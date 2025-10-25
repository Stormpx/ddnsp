package io.crowds.lib.boringtun;

import io.crowds.util.Native;
import top.dreamlike.panama.generator.annotation.NativeFunction;

import java.lang.foreign.MemorySegment;

public interface WireguardFFI {
    WireguardFFI INSTANCE = Native.nativeGenerate(WireguardFFI.class);


    /**
     * _Bool set_logging_function(void (*log_func)(char*));
     */
    @NativeFunction
    boolean set_logging_function(MemorySegment log_func);

    /**
     * struct wireguard_tunnel *new_tunnel(const char *static_private,
     *                                         const char *server_static_public,
     *                                         const char *preshared_key,
     *                                         uint16_t keep_alive, // Keep alive interval in seconds
     *                                         uint32_t index);      // The 24bit index prefix to be used for session indexes
     */
    @NativeFunction
    MemorySegment new_tunnel(String static_private,String server_static_public,String preshared_key,short keep_alive,int index);

    /**
     * void tunnel_free(struct wireguard_tunnel *);
     */
    @NativeFunction
    void tunnel_free(MemorySegment wireguard_tunnel);

    /**
     * struct wireguard_result wireguard_write(const struct wireguard_tunnel *tunnel,
     *                                             const uint8_t *src,
     *                                             uint32_t src_size,
     *                                             uint8_t *dst,
     *                                             uint32_t dst_size);
     */
    @NativeFunction
    WireguardResult wireguard_write(MemorySegment tunnel, MemorySegment src, int src_size, MemorySegment dst, int dst_size);

    /**
     * struct wireguard_result wireguard_read(const struct wireguard_tunnel *tunnel,
     *                                            const uint8_t *src,
     *                                            uint32_t src_size,
     *                                            uint8_t *dst,
     *                                            uint32_t dst_size);
     */
    @NativeFunction
    WireguardResult wireguard_read(MemorySegment tunnel,MemorySegment src,int src_size,MemorySegment dst,int dst_size);

    /**
     * struct wireguard_result wireguard_tick(const struct wireguard_tunnel *tunnel,
     *                                            uint8_t *dst,
     *                                            uint32_t dst_size);
     */
    @NativeFunction
    WireguardResult wireguard_tick(MemorySegment tunnel,MemorySegment dst,int dst_size);

}
