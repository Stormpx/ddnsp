package io.crowds.lib.xdp.ffi;

import io.crowds.util.Native;
import top.dreamlike.panama.generator.annotation.NativeFunction;
import top.dreamlike.panama.generator.annotation.Pointer;

import java.lang.foreign.MemorySegment;

public interface LibBpf {
    LibBpf INSTANCE = Native.nativeGenerate(LibBpf.class);


    /**
     * struct bpf_map *bpf_object__find_map_by_name(const struct bpf_object *obj, const char *name);
     */
    @NativeFunction(returnIsPointer = true)
    BpfMap bpf_object__find_map_by_name(@Pointer BpfObject obj, MemorySegment name);


    /**
     * int bpf_object__find_map_fd_by_name(const struct bpf_object *obj, const char *name);
     */
    @NativeFunction
    int bpf_object__find_map_fd_by_name(@Pointer BpfObject obj, MemorySegment name);

    /**
     * bpf_map *bpf_object__next_map(const struct bpf_object *obj, const struct bpf_map *map);
     */
    @NativeFunction(returnIsPointer = true)
    BpfMap bpf_object__next_map(@Pointer BpfObject obj, @Pointer BpfMap map);

    /**
     * int bpf_map__fd(const struct bpf_map *map);
     */
    @NativeFunction
    int bpf_map__fd(@Pointer BpfMap map);

    /**
     * const char *bpf_map__name(const struct bpf_map *map);
     */
    @NativeFunction
    MemorySegment bpf_map__name(@Pointer BpfMap map);


    /**
     * int bpf_map__lookup_elem(const struct bpf_map *map,const void *key, size_t key_sz,void *value, size_t value_sz, __u64 flags);
     */
    @NativeFunction
    int bpf_map__lookup_elem(@Pointer BpfMap map, MemorySegment key, long key_sz, MemorySegment value, long value_sz, long flags);

    /**
     * int bpf_map__update_elem(const struct bpf_map *map,const void *key, size_t key_sz,const void *value, size_t value_sz, __u64 flags);
     */
    @NativeFunction
    int bpf_map__update_elem(@Pointer BpfMap map, MemorySegment key, long key_sz, MemorySegment value, long value_sz, long flags);


    /**
     * int bpf_map__delete_elem(const struct bpf_map *map,const void *key, size_t key_sz, __u64 flags);
     */
    @NativeFunction
    int bpf_map__delete_elem(@Pointer BpfMap map, MemorySegment key, long key_sz, long flags);


    /**
     * struct ring_buffer *ring_buffer__new(int map_fd, ring_buffer_sample_fn sample_cb, void *ctx,const struct ring_buffer_opts *opts);
     */
    @NativeFunction(needErrorNo = true)
    MemorySegment ring_buffer__new(int map_fd, MemorySegment sample_cb, MemorySegment ctx, MemorySegment opts);

    /**
     * void ring_buffer__free(struct ring_buffer *rb);
     */
    @NativeFunction
    void ring_buffer__free(MemorySegment rb);

    /**
     * int ring_buffer__add(struct ring_buffer *rb, int map_fd,ring_buffer_sample_fn sample_cb, void *ctx);
     */
    @NativeFunction
    int ring_buffer__add(MemorySegment rb, int map_fd, MemorySegment sample_cb,MemorySegment ctx);

    /**
     * int ring_buffer__poll(struct ring_buffer *rb, int timeout_ms);
     */
    @NativeFunction
    int ring_buffer__poll(MemorySegment rb, int timeout_ms);

    /**
     * int ring_buffer__consume(struct ring_buffer *rb);
     */
    @NativeFunction
    int ring_buffer__consume(MemorySegment rb);

    /**
     * int ring_buffer__epoll_fd(const struct ring_buffer *rb);
     */
    @NativeFunction
    int ring_buffer__epoll_fd(MemorySegment rb);


    /**
     * int libbpf_strerror(int err, char *buf, size_t size)
     */
    @NativeFunction(fast = true)
    int libbpf_strerror(int err, MemorySegment buf, long size);

    default int libbpf_strerror(int err, MemorySegment buf){
        return libbpf_strerror(err,buf,buf.byteSize());
    }
}
