package io.crowds.lib.xdp;

import io.crowds.lib.xdp.ffi.XdpDesc;
import io.crowds.lib.xdp.ffi.Xsk;
import io.crowds.lib.xdp.ffi.XskRing;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public record XskConsRing(XskRing ring) {

    public int avail(int nb){
        return Xsk.INSTANCE.xsk_cons_nb_avail(ring,nb);
    }

    public int peek(int nb, MemorySegment idx){
        return Xsk.INSTANCE.xsk_ring_cons__peek(ring,nb,idx);
    }

    public XdpDesc desc(int idx){
        return Xsk.INSTANCE.xsk_ring_cons__rx_desc(ring,idx);
    }

    public long addr(int idx){
        MemorySegment addr = Xsk.INSTANCE.xsk_ring_cons__comp_addr(ring, idx);
        return addr.reinterpret(ValueLayout.JAVA_LONG.byteSize()).get(ValueLayout.JAVA_LONG,0);
    }

    public void release(int nb){
        Xsk.INSTANCE.xsk_ring_cons__release(ring,nb);
    }

}
