package io.crowds.lib.xdp.ffi;

import top.dreamlike.panama.generator.annotation.Union;

//struct xsk_socket_config {
//	__u32 rx_size;
//	__u32 tx_size;
//	union {
//		__u32 libbpf_flags;
//		__u32 libxdp_flags;
//	};
//	__u32 xdp_flags;
//	__u16 bind_flags;
//};
public class XskSocketConfig {

    private int rx_size;
    private int tx_size;
    private LibFlags libFlags;
    private int xdp_flags;
    private short bind_flags;

    @Union
    public static class LibFlags{
        private int libbpf_flags;
        private int libxdp_flags;

        public int getLibbpf_flags() {
            return libbpf_flags;
        }

        public void setLibbpf_flags(int libbpf_flags) {
            this.libbpf_flags = libbpf_flags;
        }

        public int getLibxdp_flags() {
            return libxdp_flags;
        }

        public void setLibxdp_flags(int libxdp_flags) {
            this.libxdp_flags = libxdp_flags;
        }
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

    public LibFlags getLibFlags() {
        return libFlags;
    }

    public void setLibFlags(LibFlags libFlags) {
        this.libFlags = libFlags;
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
