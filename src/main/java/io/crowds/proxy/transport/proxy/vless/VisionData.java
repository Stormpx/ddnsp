package io.crowds.proxy.transport.proxy.vless;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCounted;


public record VisionData(byte command, ByteBuf data, boolean padding, boolean longPadding) implements ReferenceCounted {
    @Override
    public int refCnt() {
        return data.refCnt();
    }

    @Override
    public ReferenceCounted retain() {
        return data.retain();
    }

    @Override
    public ReferenceCounted retain(int increment) {
        return data.retain(increment);
    }

    @Override
    public ReferenceCounted touch() {
        return data.touch();
    }

    @Override
    public ReferenceCounted touch(Object hint) {
        return data.touch(hint);
    }

    @Override
    public boolean release() {
        return data.release();
    }

    @Override
    public boolean release(int decrement) {
        return data.release();
    }
}
