package io.crowds.lib.xdp;

import io.crowds.lib.xdp.ffi.Xsk;
import io.crowds.lib.xdp.ffi.XskRing;

import java.lang.foreign.MemorySegment;

public record Umem(MemorySegment umem, MemorySegment buffer, int chunks, int chunkSize, XskRing fill, XskRing comp) {

    public int fd(){
        return Xsk.INSTANCE.xsk_umem__fd(umem);
    }

    public long fixAddr(long addr){
        return (addr/chunkSize) * chunkSize;
    }

    public MemorySegment getData(long addr){
        return Xsk.INSTANCE.xsk_umem__get_data(buffer, addr).reinterpret(chunkSize);
    }

    public void delete() {
        Xsk.INSTANCE.xsk_umem__delete(umem);
    }
}
