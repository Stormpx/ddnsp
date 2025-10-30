package io.crowds.lib.xdp;

import io.crowds.lib.xdp.ffi.Xsk;
import io.crowds.lib.xdp.ffi.XskRing;

import java.lang.foreign.MemorySegment;

public record XdpSocket(MemorySegment xsk, Umem umemInfo, XskProdRing tx, XskConsRing rx, XskProdRing fill, XskConsRing comp) {

    public XdpSocket(MemorySegment xsk, Umem umemInfo, XskRing tx, XskRing rx, XskRing fill, XskRing comp) {
        this(xsk,umemInfo,new XskProdRing(tx),new XskConsRing(rx),new XskProdRing(fill),new XskConsRing(comp));
    }

    public int fd(){
        return Xsk.INSTANCE.xsk_socket__fd(xsk);
    }

    public void delete(){
        Xsk.INSTANCE.xsk_socket__delete(xsk);
    }


}
