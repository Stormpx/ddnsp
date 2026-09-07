package io.crowds.lib.xdp.ffi;

import top.dreamlike.panama.generator.annotation.Pointer;

import java.lang.foreign.MemorySegment;

//struct xdp_program_opts {
//	size_t sz;
//	struct bpf_object *obj;
//	struct bpf_object_open_opts *opts;
//	const char *prog_name;
//	const char *find_filename;
//	const char *open_filename;
//	const char *pin_path;
//	__u32 id;
//	int fd;
//	size_t :0;
//};
public class XdpProgramOpts {
    private long sz;
    @Pointer
    private MemorySegment obj;
    @Pointer
    private MemorySegment opts;
    @Pointer
    private MemorySegment prog_name;
    @Pointer
    private MemorySegment find_filename;
    @Pointer
    private MemorySegment open_filename;
    @Pointer
    private MemorySegment pin_path;
    private int id;
    private int fd;

    public long getSz() {
        return sz;
    }

    public void setSz(long sz) {
        this.sz = sz;
    }

    public MemorySegment getObj() {
        return obj;
    }

    public void setObj(MemorySegment obj) {
        this.obj = obj;
    }

    public MemorySegment getOpts() {
        return opts;
    }

    public void setOpts(MemorySegment opts) {
        this.opts = opts;
    }

    public MemorySegment getProg_name() {
        return prog_name;
    }

    public void setProg_name(MemorySegment prog_name) {
        this.prog_name = prog_name;
    }

    public MemorySegment getFind_filename() {
        return find_filename;
    }

    public void setFind_filename(MemorySegment find_filename) {
        this.find_filename = find_filename;
    }

    public MemorySegment getOpen_filename() {
        return open_filename;
    }

    public void setOpen_filename(MemorySegment open_filename) {
        this.open_filename = open_filename;
    }

    public MemorySegment getPin_path() {
        return pin_path;
    }

    public void setPin_path(MemorySegment pin_path) {
        this.pin_path = pin_path;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getFd() {
        return fd;
    }

    public void setFd(int fd) {
        this.fd = fd;
    }
}
