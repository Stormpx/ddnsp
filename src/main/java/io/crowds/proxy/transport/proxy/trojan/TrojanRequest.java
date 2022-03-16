package io.crowds.proxy.transport.proxy.trojan;

import io.crowds.proxy.transport.Destination;
import io.crowds.util.Hash;
import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.DatagramPacket;

import java.nio.charset.StandardCharsets;

public class TrojanRequest {

    private byte[] password;
    private Destination destination;
    private Object payload;

//    public TrojanRequest(String password, Destination destination) {
//        var bytes=Hash.sha224(password.getBytes(StandardCharsets.UTF_8));
//        this.password = Hex.encode(bytes);
//        this.destination = destination;
//    }

    public TrojanRequest(byte[] password, Destination destination) {
        this.password = password;
        this.destination = destination;
    }

    public Object getPayload() {
        return payload;
    }

    public TrojanRequest setPayload(ByteBuf payload) {
        this.payload = payload;
        return this;
    }

    public TrojanRequest setPayload(DatagramPacket payload) {
        this.payload = payload;
        return this;
    }

    public byte[] getPassword() {
        return password;
    }

    public Destination getDestination() {
        return destination;
    }


}
