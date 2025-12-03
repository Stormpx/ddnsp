package io.crowds.lib.xdp.ffi;

//struct xsk_umem_opts {
//	size_t sz;
//	int fd;
//	__u64 size;
//	__u32 fill_size;
//	__u32 comp_size;
//	__u32 frame_size;
//	__u32 frame_headroom;
//	__u32 flags;
//	__u32 tx_metadata_len;
//	size_t :0;
//};
public class XskUmemOpts {
    private long sz;
    private int fd;
    private long size;
    private int fill_size;
    private int comp_size;
    private int frame_size;
    private int frame_headroom;
    private int flags;
    private int tx_metadata_len;

    public long getSz() {
        return sz;
    }

    public void setSz(long sz) {
        this.sz = sz;
    }

    public int getFd() {
        return fd;
    }

    public void setFd(int fd) {
        this.fd = fd;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public int getFill_size() {
        return fill_size;
    }

    public void setFill_size(int fill_size) {
        this.fill_size = fill_size;
    }

    public int getComp_size() {
        return comp_size;
    }

    public void setComp_size(int comp_size) {
        this.comp_size = comp_size;
    }

    public int getFrame_size() {
        return frame_size;
    }

    public void setFrame_size(int frame_size) {
        this.frame_size = frame_size;
    }

    public int getFrame_headroom() {
        return frame_headroom;
    }

    public void setFrame_headroom(int frame_headroom) {
        this.frame_headroom = frame_headroom;
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public int getTx_metadata_len() {
        return tx_metadata_len;
    }

    public void setTx_metadata_len(int tx_metadata_len) {
        this.tx_metadata_len = tx_metadata_len;
    }
}
