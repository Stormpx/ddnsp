package io.crowds.lib.xdp.ffi;

import io.crowds.util.Native;
import top.dreamlike.panama.generator.annotation.NativeFunction;
import top.dreamlike.panama.generator.annotation.Pointer;
import top.dreamlike.panama.generator.proxy.StructProxyGenerator;

import java.lang.foreign.MemorySegment;

public interface LibXdp {
    LibXdp INSTANCE = Native.nativeGenerate(LibXdp.class);

    /**
     * struct xdp_program *xdp_program__find_file(const char *filename,
     * 					   const char *section_name,
     * 					   struct bpf_object_open_opts *opts);
     */
    @NativeFunction(returnIsPointer = true)
    XdpProgram xdp_program__find_file(MemorySegment filename, MemorySegment section_name, MemorySegment opts);
    /**
     * struct xdp_program *xdp_program__open_file(const char *filename,
     * 					   const char *section_name,
     * 					   struct bpf_object_open_opts *opts)
     */
    @NativeFunction(returnIsPointer = true)
    XdpProgram xdp_program__open_file(MemorySegment filename, MemorySegment section_name, MemorySegment opts);

    /**
     * void xdp_program__close(struct xdp_program *xdp_prog);
     */
    @NativeFunction
    void xdp_program__close(@Pointer XdpProgram xdp_prog);


    /**
     * enum xdp_attach_mode xdp_program__is_attached(const struct xdp_program *xdp_prog,int ifindex);
     */
    @NativeFunction
    int xdp_program__is_attached(@Pointer XdpProgram xdp_prog, int ifindex);

    /**
     * const char *xdp_program__name(const struct xdp_program *xdp_prog);
     */
    @NativeFunction(fast = true)
    MemorySegment xdp_program__name(@Pointer XdpProgram xdp_prog);

    /**
     * const unsigned char *xdp_program__tag(const struct xdp_program *xdp_prog);
     */
    @NativeFunction(fast = true)
    MemorySegment xdp_program__tag(@Pointer XdpProgram xdp_prog);

    /**
     * struct bpf_object *xdp_program__bpf_obj(struct xdp_program *xdp_prog);
     */
    @NativeFunction(fast = true,returnIsPointer = true)
    BpfObject xdp_program__bpf_obj(@Pointer XdpProgram xdp_prog);


    /**
     * const struct btf *xdp_program__btf(struct xdp_program *xdp_prog);
     */
    @NativeFunction(fast = true)
    MemorySegment xdp_program__btf(@Pointer XdpProgram xdp_prog);

    /**
     * uint32_t xdp_program__id(const struct xdp_program *xdp_prog);
     */
    @NativeFunction(fast = true)
    int xdp_program__id(@Pointer XdpProgram xdp_prog);

    /**
     * int xdp_program__fd(const struct xdp_program *xdp_prog);
     */
    @NativeFunction(fast = true)
    int xdp_program__fd(@Pointer XdpProgram xdp_prog);


    /**
     * unsigned int xdp_program__run_prio(const struct xdp_program *xdp_prog);
     */
    @NativeFunction(fast = true)
    int xdp_program__run_prio(@Pointer XdpProgram xdp_prog);


    /**
     * int xdp_program__attach(struct xdp_program *prog, int ifindex,enum xdp_attach_mode mode,unsigned int flags)
     */
    @NativeFunction
    int xdp_program__attach(@Pointer XdpProgram prog, int ifindex, int mode, int flags);

    /**
     * int xdp_program__detach(struct xdp_program *prog, int ifindex,enum xdp_attach_mode mode,unsigned int flags)
     */
    @NativeFunction
    int xdp_program__detach(@Pointer XdpProgram prog, int ifindex, int mode, int flags);

    /**
     * struct xdp_multiprog *xdp_multiprog__get_from_ifindex(int ifindex);
     */
    @NativeFunction(returnIsPointer = true)
    XdpMultiProg xdp_multiprog__get_from_ifindex(int ifindex);

    /**
     * struct xdp_program *xdp_multiprog__next_prog(const struct xdp_program *prog,const struct xdp_multiprog *mp);
     */
    @NativeFunction(returnIsPointer = true)
    XdpProgram xdp_multiprog__next_prog(@Pointer XdpProgram prog, @Pointer XdpMultiProg mp);

    /**
     * enum xdp_attach_mode xdp_multiprog__attach_mode(const struct xdp_multiprog *mp)
     */
    int xdp_multiprog__attach_mode(@Pointer XdpMultiProg mp);

    /**
     * void xdp_multiprog__close(struct xdp_multiprog *mp);
     */
    @NativeFunction
    void xdp_multiprog__close(@Pointer XdpMultiProg mp);

    /**
     * int xdp_multiprog__detach(struct xdp_multiprog *mp);
     */
    @NativeFunction
    int xdp_multiprog__detach(@Pointer XdpMultiProg mp);

    /**
     * long libxdp_get_error(const void *ptr)
     */
    @NativeFunction(fast = true)
    long libxdp_get_error(MemorySegment ptr);;

    default long libxdp_get_error(XdpProgram ptr){
        return libxdp_get_error(StructProxyGenerator.findMemorySegment(ptr));
    }
    default long libxdp_get_error(XdpMultiProg ptr){
        return libxdp_get_error(StructProxyGenerator.findMemorySegment(ptr));
    }

    /**
     * int libxdp_strerror(int err, char *buf, size_t size)
     */
    @NativeFunction(fast = true)
    int libxdp_strerror(int err, MemorySegment buf, long size);

    default int libxdp_strerror(int err, MemorySegment buf){
        return libxdp_strerror(err,buf,buf.byteSize());
    }
}
