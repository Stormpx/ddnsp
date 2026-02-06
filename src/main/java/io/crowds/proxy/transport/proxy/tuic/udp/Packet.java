package io.crowds.proxy.transport.proxy.tuic.udp;

import io.crowds.proxy.NetAddr;
import io.netty.buffer.ByteBuf;

public record Packet(int pktId,NetAddr addr, ByteBuf data) {

}
