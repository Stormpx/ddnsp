package io.crowds.proxy.transport.proxy.tuic;

import io.crowds.proxy.NetAddr;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;

import java.util.UUID;

public sealed interface TuicCommand permits TuicCommand.Auth, TuicCommand.Connect, TuicCommand.Dissociate, TuicCommand.Packet, TuicCommand.Heartbeat {

    record Auth(UUID uuid,byte[] token) implements TuicCommand {}

    record Connect(NetAddr addr)implements TuicCommand {}

    record Packet(int assocId, int pktId, short fragTotal, short fragId, NetAddr addr, ByteBuf data)implements TuicCommand {}

    record Dissociate(int assocId)implements TuicCommand {}

    record Heartbeat()implements TuicCommand {}
}
