package io.crowds.lib.xdp.ffi;

import io.crowds.util.Native;
import top.dreamlike.panama.generator.annotation.NativeFunction;
import top.dreamlike.panama.generator.annotation.Pointer;

import java.lang.foreign.MemorySegment;

public interface Xsk {
    Xsk INSTANCE = Native.nativeGenerate(Xsk.class);

    int XSK_LIBXDP_FLAGS__INHIBIT_PROG_LOAD = 1 << 0;
    int  XSK_RING_CONS__DEFAULT_NUM_DESCS =       2048;
    int  XSK_RING_PROD__DEFAULT_NUM_DESCS =       2048;
    /* 4096 bytes */
    int  XSK_UMEM__DEFAULT_FRAME_SHIFT =     12 ;
    int  XSK_UMEM__DEFAULT_FRAME_SIZE =      (1 << XSK_UMEM__DEFAULT_FRAME_SHIFT);
    int  XSK_UMEM__DEFAULT_FRAME_HEADROOM =  0;
    int  XSK_UMEM__DEFAULT_FLAGS =  0;

    /**
     * int xsk_umem__create_with_fd(struct xsk_umem **umem,
     * 			     int fd, void *umem_area, __u64 size,
     * 			     struct xsk_ring_prod *fill,
     * 			     struct xsk_ring_cons *comp,
     * 			     const struct xsk_umem_config *config);
     */
    @NativeFunction
    int xsk_umem__create_with_fd(MemorySegment umem, int fd, MemorySegment umem_area, long size,
                                 @Pointer XskRing fill, @Pointer XskRing comp, @Pointer XskUmemConfig config);

    default int xsk_umem__create(MemorySegment umem, MemorySegment umem_area, long size,
                                 @Pointer XskRing fill, @Pointer XskRing comp,
                                 @Pointer XskUmemConfig config){
        return xsk_umem__create_with_fd(umem,0,umem_area,size,fill,comp,config);
    }

    /**
     * int xsk_umem__fd(const struct xsk_umem *umem);
     */
    @NativeFunction
    int xsk_umem__fd(MemorySegment umem);
    /**
     * int xsk_umem__delete(struct xsk_umem *umem);
     * @param umem
     * @return
     */
    @NativeFunction
    int xsk_umem__delete(MemorySegment umem);


    /**
     * int xsk_socket__create(struct xsk_socket **xsk_ptr, const char *ifname,
     * 		       __u32 queue_id, struct xsk_umem *umem,
     * 		       struct xsk_ring_cons *rx, struct xsk_ring_prod *tx,
     * 		       const struct xsk_socket_config *usr_config)
     */
    @NativeFunction
    int xsk_socket__create(MemorySegment xsk_ptr, MemorySegment ifname,
                           int queue_id, MemorySegment umem,
                           @Pointer XskRing rx, @Pointer XskRing tx,
                          @Pointer XskSocketConfig usr_config);

    /**
     * int xsk_socket__create_shared(struct xsk_socket **xsk_ptr,
     * 			      const char *ifname,
     * 			      __u32 queue_id, struct xsk_umem *umem,
     * 			      struct xsk_ring_cons *rx,
     * 			      struct xsk_ring_prod *tx,
     * 			      struct xsk_ring_prod *fill,
     * 			      struct xsk_ring_cons *comp,
     * 			      const struct xsk_socket_config *usr_config)
     */
    @NativeFunction
    int xsk_socket__create_shared(MemorySegment xsk_ptr, MemorySegment ifname, int queue_id, MemorySegment umem,
                           @Pointer XskRing rx, @Pointer XskRing tx,@Pointer XskRing fill, @Pointer XskRing comp,
                           @Pointer XskSocketConfig usr_config);

    /**
     * int xsk_socket__fd(const struct xsk_socket *xsk);
     */
    @NativeFunction(fast = true)
    int xsk_socket__fd(MemorySegment xsk);

    /**
     * void xsk_socket__delete(struct xsk_socket *xsk);
     */
    @NativeFunction(fast = true)
    void xsk_socket__delete(MemorySegment xsk);

    /**
     * int xsk_socket__update_xskmap(struct xsk_socket *xsk, int xsks_map_fd);
     */
    @NativeFunction
    int xsk_socket__update_xskmap(MemorySegment xsk, int xsks_map_fd);

    /**
     * __u32 xsk_prod_nb_free(struct xsk_ring_prod *r, __u32 nb)
     */
    @NativeFunction(fast = true)
    int xsk_prod_nb_free(@Pointer XskRing r, int nb);

    /**
     * __u32 xsk_cons_nb_avail(struct xsk_ring_cons *r, __u32 nb)
     */
    @NativeFunction(fast = true)
    int xsk_cons_nb_avail(@Pointer XskRing r, int nb);

    /**
     * __u32 xsk_ring_prod__reserve(struct xsk_ring_prod *prod, __u32 nb, __u32 *idx);
     */
    @NativeFunction(fast = true)
    int xsk_ring_prod__reserve(@Pointer XskRing prod, int nb, MemorySegment idx);

    /**
     * void xsk_ring_prod__submit(struct xsk_ring_prod *prod, __u32 nb);
     */
    @NativeFunction(fast = true)
    void xsk_ring_prod__submit(@Pointer XskRing prod, int nb);

    /**
     * __u32 xsk_ring_cons__peek(struct xsk_ring_cons *cons, __u32 nb, __u32 *idx);
     */
    @NativeFunction(fast = true)
    int xsk_ring_cons__peek(@Pointer XskRing cons, int nb, MemorySegment idx);

    /**
     * void xsk_ring_cons__cancel(struct xsk_ring_cons *cons, __u32 nb);
     */
    @NativeFunction(fast = true)
    void xsk_ring_cons__cancel(@Pointer XskRing cons, int nb);

    /**
     * void xsk_ring_cons__release(struct xsk_ring_cons *cons, __u32 nb);
     */
    @NativeFunction(fast = true)
    void xsk_ring_cons__release(@Pointer XskRing cons, int nb);

    /**
     * __u64 *xsk_ring_prod__fill_addr(struct xsk_ring_prod *fill, __u32 idx);
     */
    @NativeFunction(fast = true)
    MemorySegment xsk_ring_prod__fill_addr(@Pointer XskRing fill, int idx);
    /**
     * struct xdp_desc *xsk_ring_prod__tx_desc(struct xsk_ring_prod *tx, __u32 idx);
     */
    @NativeFunction(returnIsPointer = true)
    XdpDesc xsk_ring_prod__tx_desc(@Pointer XskRing tx, int idx);
    /**
     * const __u64 *xsk_ring_cons__comp_addr(const struct xsk_ring_cons *comp, __u32 idx);
     */
    @NativeFunction(fast = true)
    MemorySegment xsk_ring_cons__comp_addr(@Pointer XskRing comp, int idx);

    /**
     * const struct xdp_desc *xsk_ring_cons__rx_desc(const struct xsk_ring_cons *rx, __u32 idx);
     */
    @NativeFunction(returnIsPointer = true)
    XdpDesc xsk_ring_cons__rx_desc(@Pointer XskRing rx, int idx);

    /**
     * void *xsk_umem__get_data(void *umem_area, __u64 addr)
     */
    @NativeFunction(fast = true)
    MemorySegment xsk_umem__get_data(MemorySegment umem_area, long addr);

    /**
     * __u64 xsk_umem__extract_addr(__u64 addr)
     */
    @NativeFunction(fast = true)
    long xsk_umem__extract_addr(long addr);

    /**
     * __u64 xsk_umem__extract_offset(__u64 addr)
     */
    @NativeFunction(fast = true)
    long xsk_umem__extract_offset(long addr);

    /**
     * __u64 xsk_umem__add_offset_to_addr(__u64 addr)
     */
    @NativeFunction(fast = true)
    long xsk_umem__add_offset_to_addr(long addr);


}
