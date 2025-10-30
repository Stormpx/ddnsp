package io.crowds.lib.xdp.ffi;

//struct xsk_umem_config {
//	__u32 fill_size;
//	__u32 comp_size;
//	__u32 frame_size;
//	__u32 frame_headroom;
//	__u32 flags;
//};
public class XskUmemConfig {
    private int fill_size;
    private int comp_size;
    private int frame_size;
    private int frame_headroom;
    private int flags;

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
}
