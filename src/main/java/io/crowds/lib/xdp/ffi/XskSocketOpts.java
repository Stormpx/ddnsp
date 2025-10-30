package io.crowds.lib.xdp.ffi;


import top.dreamlike.panama.generator.annotation.Pointer;

import java.lang.foreign.MemorySegment;

//struct xsk_socket_opts {
//	size_t sz;
//	struct xsk_ring_cons *rx;
//	struct xsk_ring_prod *tx;
//	struct xsk_ring_prod *fill;
//	struct xsk_ring_cons *comp;
//	__u32 rx_size;
//	__u32 tx_size;
//	__u32 libxdp_flags;
//	__u32 xdp_flags;
//	__u16 bind_flags;
//	size_t :0;
//};
public class XskSocketOpts {
    private long sz;
    @Pointer
    private XskRing rx;
    @Pointer
    private XskRing tx;
    @Pointer
    private XskRing fill;
    @Pointer
    private XskRing comp;
    private int rx_size;
    private int tx_size;
    private int libxdp_flags;
    private int xdp_flags;
    private short bind_flags;

    public long getSz() {
        return sz;
    }

    public void setSz(long sz) {
        this.sz = sz;
    }

    public XskRing getRx() {
        return rx;
    }

    public void setRx(XskRing rx) {
        this.rx = rx;
    }

    public XskRing getTx() {
        return tx;
    }

    public void setTx(XskRing tx) {
        this.tx = tx;
    }

    public XskRing getFill() {
        return fill;
    }

    public void setFill(XskRing fill) {
        this.fill = fill;
    }

    public XskRing getComp() {
        return comp;
    }

    public void setComp(XskRing comp) {
        this.comp = comp;
    }

    public int getRx_size() {
        return rx_size;
    }

    public void setRx_size(int rx_size) {
        this.rx_size = rx_size;
    }

    public int getTx_size() {
        return tx_size;
    }

    public void setTx_size(int tx_size) {
        this.tx_size = tx_size;
    }

    public int getLibxdp_flags() {
        return libxdp_flags;
    }

    public void setLibxdp_flags(int libxdp_flags) {
        this.libxdp_flags = libxdp_flags;
    }

    public int getXdp_flags() {
        return xdp_flags;
    }

    public void setXdp_flags(int xdp_flags) {
        this.xdp_flags = xdp_flags;
    }

    public short getBind_flags() {
        return bind_flags;
    }

    public void setBind_flags(short bind_flags) {
        this.bind_flags = bind_flags;
    }
}
