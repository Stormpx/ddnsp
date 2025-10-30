package io.crowds.compoments.xdp;

import java.lang.foreign.MemorySegment;

public interface XdpIngressHandler {

    default boolean isZeroCopy(){
        return false;
    }

    void handle(MemorySegment ingress);

    void complete();

}
