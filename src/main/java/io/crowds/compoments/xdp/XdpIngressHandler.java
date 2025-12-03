package io.crowds.compoments.xdp;


public interface XdpIngressHandler {

    default boolean isZeroCopy(){
        return false;
    }

    void handle(RxDesc rxDesc);

    void complete();

}
