package io.crowds.lib.xdp.ffi;

import io.crowds.lib.xdp.Umem;

import java.lang.foreign.MemorySegment;

//struct xdp_desc {
//	__u64 addr;
//	__u32 len;
//	__u32 options;
//};
public class XdpDesc {
    public static final int XDP_PKT_CONTD = (1 << 0);
    public static final int XDP_TX_METADATA = (1 << 1);

    private long addr;
    private int len;
    private int options;

    public long getUmemAddr(){
        return Xsk.INSTANCE.xsk_umem__extract_addr(getAddr());
    }

    public long getDataAddr(){
        return Xsk.INSTANCE.xsk_umem__add_offset_to_addr(getAddr());
    }

    public MemorySegment getData(Umem umem){
        return umem.getData(getDataAddr()).reinterpret(getLen());
    }

    public long getAddr() {
        return addr;
    }

    public void setAddr(long addr) {
        this.addr = addr;
    }

    public int getLen() {
        return len;
    }

    public void setLen(int len) {
        this.len = len;
    }

    public int getOptions() {
        return options;
    }

    public void setOptions(int options) {
        this.options = options;
    }
}
