package io.crowds.compoments.capsule;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;

public class Capsule extends DefaultByteBufHolder {
    public final static int TYPE_DATAGRAM = 0x00;

    private final int type;

    public Capsule(int type, ByteBuf data) {
        super(data);
        this.type = type;
    }

    public static Capsule datagram(ByteBuf data){
        return new Capsule(TYPE_DATAGRAM,data);
    }

    public int type() {
        return type;
    }

    @Override
    public Capsule copy() {
        super.copy();
        return this;
    }

    @Override
    public Capsule duplicate() {
        super.duplicate();
        return this;
    }

    @Override
    public Capsule retainedDuplicate() {
        super.retainedDuplicate();
        return this;
    }

    @Override
    public Capsule replace(ByteBuf content) {
        super.replace(content);
        return this;
    }

    @Override
    public Capsule retain() {
        super.retain();
        return this;
    }

    @Override
    public Capsule retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public Capsule touch() {
        super.touch();
        return this;
    }

    @Override
    public Capsule touch(Object hint) {
        super.touch(hint);
        return this;
    }
}
