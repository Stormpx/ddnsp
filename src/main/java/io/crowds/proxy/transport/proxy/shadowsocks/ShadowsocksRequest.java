package io.crowds.proxy.transport.proxy.shadowsocks;

import io.crowds.proxy.transport.Destination;
import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.DatagramPacket;


public class ShadowsocksRequest {
    //tcp desired destination address
    //udp server address
    private Destination destination;
    private Object payload;

    public ShadowsocksRequest(Destination destination) {
        this.destination = destination;
    }

    public Destination getDestination() {
        return destination;
    }

    public ShadowsocksRequest setDestination(Destination destination) {
        this.destination = destination;
        return this;
    }

    public Object getPayload() {
        return payload;
    }

    public ShadowsocksRequest setPayload(ByteBuf payload) {
        this.payload = payload;
        return this;
    }

    public ShadowsocksRequest setPayload(DatagramPacket payload) {
        this.payload = payload;
        return this;
    }
}
