package io.crowds.compoments.xdp;

import org.stormpx.net.buffer.ByteArray;

public record TxDesc(int csumStart, int csumOffset, boolean timestamp, ByteArray data) {


    boolean isFillMeta(){
        return isRequestChecksum() || isRequestTimestamp();
    }

    boolean isRequestTimestamp(){
        return timestamp;
    }

    boolean isRequestChecksum(){
        return csumStart!=-1;
    }

}
