package io.crowds.lib.xdp;

import io.crowds.lib.xdp.ffi.XdpDesc;
import io.crowds.lib.xdp.ffi.Xsk;
import io.crowds.lib.xdp.ffi.XskRing;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public record XskProdRing(XskRing ring) {

    public boolean needsWakeup(){
        return Xsk.INSTANCE.xsk_ring_prod__needs_wakeup(ring)==1;
    }

    public int nbFree(int nb){
        return Xsk.INSTANCE.xsk_prod_nb_free(ring,nb);
    }

    public int reserve(int nb, MemorySegment idx){
        return Xsk.INSTANCE.xsk_ring_prod__reserve(ring,nb,idx);
    }


    public XdpDesc desc(int idx){
        return Xsk.INSTANCE.xsk_ring_prod__tx_desc(ring,idx);
    }

    public void addr(int idx,long addr){
        Xsk.INSTANCE.xsk_ring_prod__fill_addr(ring, idx)
                    .reinterpret(ValueLayout.JAVA_LONG.byteSize())
                    .set(ValueLayout.JAVA_LONG,0,addr);
    }

    public void submit(int nb){
        Xsk.INSTANCE.xsk_ring_prod__submit(ring,nb);
    }

}
