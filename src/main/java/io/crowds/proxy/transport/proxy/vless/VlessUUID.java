package io.crowds.proxy.transport.proxy.vless;

import io.crowds.util.Bufs;
import io.crowds.util.Hash;
import io.crowds.util.Strs;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;

public class VlessUUID {



    public static UUID of(String text){
        if (Strs.isBlank(text)) {
            throw new IllegalArgumentException("invalid vless uuid string");
        }
        if (text.length()==36){
            return UUID.fromString(text);
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length>30){
            throw new IllegalArgumentException("invalid vless uuid string "+ text);
        }
        var in = new byte[16+ bytes.length];
        System.arraycopy(bytes,0,in,16,bytes.length);
        byte[] out = Hash.sha1(in);
        out[6] = (byte) ((out[6] & 0x0f) | (5 << 4));
        out[8] = (byte) (out[8]&(0xff>>2) | (0x02 << 6));

        return new UUID(Bufs.readLong(out,0),Bufs.readLong(out,8));
    }

}
