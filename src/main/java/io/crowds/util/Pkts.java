package io.crowds.util;

import org.stormpx.net.buffer.ByteArray;

import java.nio.ByteBuffer;

public class Pkts {


    public static boolean isIPv4(ByteArray buffer){
        var version = buffer.get(0) >> 4;
        return version==4;
    }

    public static boolean isIPv6(ByteArray buffer){
        var version = buffer.get(0) >> 4;
        return version==6;
    }

    public static byte[] getDestinationAddress(ByteArray buffer){
        if (isIPv4(buffer)){
            byte[] bytes = new byte[4];
            buffer.getBytes(16,bytes,0,bytes.length);
            return bytes;
        }else if (isIPv6(buffer)){
            byte[] bytes = new byte[16];
            buffer.getBytes(24,bytes,0,bytes.length);
            return bytes;
        }
        return null;
    }

    public static ByteBuffer toDirectBuffer(ByteArray buffer){
        ByteBuffer[] buffers = buffer.nioBuffers(0, buffer.length());
        if (buffers.length==1&&buffers[0].isDirect()){
            return buffers[0];
        }
        ByteBuffer result = ByteBuffer.allocateDirect(buffer.length());
        for (ByteBuffer buffer1 : buffers) {
            result.put(buffer1);
        }
        result.flip();
        return result;
    }

}
