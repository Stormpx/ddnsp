package io.crowds.lib.xdp.ffi;

import top.dreamlike.panama.generator.annotation.Pointer;

import java.lang.foreign.MemorySegment;

//struct xsk_ring { \
//	__u32 cached_prod; \
//	__u32 cached_cons; \
//	__u32 mask; \
//	__u32 size; \
//	__u32 *producer; \
//	__u32 *consumer; \
//	void *ring; \
//	__u32 *flags; \
//}
public class XskRing {
    private int cached_prod;
    private int cached_cons;
    private int mask;
    private int size;
    @Pointer
    private MemorySegment producer;
    @Pointer
    private MemorySegment consumer;
    @Pointer
    private MemorySegment ring;
    @Pointer
    private MemorySegment flags;

    public int getCached_prod() {
        return cached_prod;
    }

    public void setCached_prod(int cached_prod) {
        this.cached_prod = cached_prod;
    }

    public int getCached_cons() {
        return cached_cons;
    }

    public void setCached_cons(int cached_cons) {
        this.cached_cons = cached_cons;
    }

    public int getMask() {
        return mask;
    }

    public void setMask(int mask) {
        this.mask = mask;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public MemorySegment getProducer() {
        return producer;
    }

    public void setProducer(MemorySegment producer) {
        this.producer = producer;
    }

    public MemorySegment getConsumer() {
        return consumer;
    }

    public void setConsumer(MemorySegment consumer) {
        this.consumer = consumer;
    }

    public MemorySegment getRing() {
        return ring;
    }

    public void setRing(MemorySegment ring) {
        this.ring = ring;
    }

    public MemorySegment getFlags() {
        return flags;
    }

    public void setFlags(MemorySegment flags) {
        this.flags = flags;
    }
}
