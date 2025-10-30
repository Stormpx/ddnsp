package io.crowds.compoments.xdp;

import top.dreamlike.panama.generator.annotation.NativeArrayMark;
import top.dreamlike.panama.generator.annotation.Union;

import java.lang.foreign.MemorySegment;

//struct mac_message{
//	unsigned char source[ETH_ALEN];
//	unsigned char dest[ETH_ALEN];
//	unsigned char mac[ETH_ALEN];
//	__u32 type; //4 for ipv4, 6 for ipv6
//	union{
//		struct {
//			__be32 addr;
//		} ipv4;
//		struct {
//			struct in6_addr source;
//			struct in6_addr dest;
//			__be32 flags;
//			struct in6_addr addr;
//		} ipv6 ;
//	};
//};

public class MacMessage {

    @NativeArrayMark(size = byte.class, length = 6)
    private MemorySegment source;
    @NativeArrayMark(size = byte.class, length = 6)
    private MemorySegment dest;
    @NativeArrayMark(size = byte.class, length = 6)
    private MemorySegment mac;
    private int type;
    private UnionIp ip;


    @Union
    public static class UnionIp{
        private Ipv4 ipv4;
        private Ipv6 ipv6;

        public Ipv4 getIpv4() {
            return ipv4;
        }

        public void setIpv4(Ipv4 ipv4) {
            this.ipv4 = ipv4;
        }

        public Ipv6 getIpv6() {
            return ipv6;
        }

        public void setIpv6(Ipv6 ipv6) {
            this.ipv6 = ipv6;
        }
    }

    public static class Ipv4{
        @NativeArrayMark(size = byte.class, length = 4)
        private MemorySegment addr;

        public MemorySegment getAddr() {
            return addr;
        }

        public void setAddr(MemorySegment addr) {
            this.addr = addr;
        }
    }
    public static class Ipv6{
        @NativeArrayMark(size = byte.class, length = 16)
        private MemorySegment source;
        @NativeArrayMark(size = byte.class, length = 16)
        private MemorySegment dest;
        private byte flags;
        @NativeArrayMark(size = byte.class, length = 16)
        private MemorySegment addr;

        public MemorySegment getSource() {
            return source;
        }

        public void setSource(MemorySegment source) {
            this.source = source;
        }

        public MemorySegment getDest() {
            return dest;
        }

        public void setDest(MemorySegment dest) {
            this.dest = dest;
        }

        public byte getFlags() {
            return flags;
        }

        public void setFlags(byte flags) {
            this.flags = flags;
        }

        public MemorySegment getAddr() {
            return addr;
        }

        public void setAddr(MemorySegment addr) {
            this.addr = addr;
        }
    }

    public boolean isIpv6(){
        return getType()==6;
    }

    public MemorySegment getDest() {
        return dest;
    }

    public void setDest(MemorySegment dest) {
        this.dest = dest;
    }

    public MemorySegment getSource() {
        return source;
    }

    public void setSource(MemorySegment source) {
        this.source = source;
    }
    public MemorySegment getMac() {
        return mac;
    }

    public void setMac(MemorySegment mac) {
        this.mac = mac;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public UnionIp getIp() {
        return ip;
    }

    public void setIp(UnionIp ip) {
        this.ip = ip;
    }
}
