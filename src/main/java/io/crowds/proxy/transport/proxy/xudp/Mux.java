package io.crowds.proxy.transport.proxy.xudp;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.TP;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCounted;

/**
 * A Mux.Cool frame (the XUDP single session usage, sessionID is always 0).
 *
 * <pre>
 * +---------------+------------------+---------------+------------------+
 * | metaLen  (2B) | meta (metaLen B) | dataLen  (2B) | payload (dataLen)|
 * +---------------+------------------+---------------+------------------+
 * meta = sessionID(2) | status(1) | option(1) | ... status specific fields
 * </pre>
 *
 */
public sealed interface Mux extends ReferenceCounted permits Mux.New, Mux.Keep, Mux.End, Mux.KeepAlive {

    /** fixed part of meta: sessionID(2) + status(1) + option(1) */
    int MIN_META_LEN = 4;

    /** upper bound of meta a receiver must reject (a conservative value from the spec, not exact) */
    int MAX_META_LEN = 512;

    /** meta upper bound (New has to hold the longest domain plus the GlobalID) */
    int MAX_META_LEN_NEW = 271;

    /** meta upper bound (Keep has to hold the target override of the longest domain) */
    int MAX_META_LEN_KEEP = 263;

    int TYPE_NEW = 0x01;
    int TYPE_KEEP = 0x02;
    int TYPE_END = 0x03;
    int TYPE_KEEP_ALIVE = 0x04;

    /** option bitmap: bit0 = payload present */
    int OPTION_DATA = 0x01;
    /** option bitmap: bit1 = error frame */
    int OPTION_ERROR = 0x02;

    /** network field of a New frame */
    int NETWORK_TCP = 0x01;
    int NETWORK_UDP = 0x02;

    /** address types; note that mux puts the port first and the ATYP after it, the opposite of socks5 */
    int ADDR_TYPE_IPV4 = 0x01;
    int ADDR_TYPE_DOMAIN = 0x02;
    int ADDR_TYPE_IPV6 = 0x03;

    /** the domain length is a single unsigned byte */
    int MAX_DOMAIN_LEN = 255;

    /** GlobalID length, appended at the end of meta only when "UDP + data bit set" */
    int GLOBAL_ID_LEN = 8;

    int id();

    ByteBuf data();

    /** the error bit of option: only set on End frames, meaning the session ended abnormally */
    default boolean error() {
        return false;
    }

    static int type(Mux frame){
        return switch (frame){
            case New aNew -> TYPE_NEW;
            case Keep keep -> TYPE_KEEP;
            case End end -> TYPE_END;
            case KeepAlive keepAlive -> TYPE_KEEP_ALIVE;
        };
    }

    static int maxLen(int type){
        return switch (type){
            case TYPE_NEW -> MAX_META_LEN_NEW;
            case TYPE_KEEP -> MAX_META_LEN_KEEP;
            case TYPE_END, TYPE_KEEP_ALIVE -> MIN_META_LEN;
            default -> throw new IllegalArgumentException("unknown mux status: " + type);
        };
    }

    static String statusName(int type){
        return switch (type){
            case TYPE_NEW -> "New";
            case TYPE_KEEP -> "Keep";
            case TYPE_END -> "End";
            case TYPE_KEEP_ALIVE -> "KeepAlive";
            default -> "Unknown(" + type + ")";
        };
    }

    record New(int id, TP network, NetAddr addr, byte[] globalId, ByteBuf data) implements Mux{

    }

    record Keep(int id, TP network, NetAddr addr, ByteBuf data) implements Mux{

    }

    record End(int id, boolean error, ByteBuf data) implements Mux{

    }

    record KeepAlive(int id, ByteBuf data) implements Mux{

    }


    @Override
    default int refCnt() {
        return data()==null?0:data().refCnt();
    }

    @Override
    default ReferenceCounted retain() {
        ByteBuf data = data();
        if (data !=null){
            data.retain();
        }
        return this;
    }

    @Override
    default ReferenceCounted retain(int increment) {
        ByteBuf data = data();
        if (data !=null){
            data.retain(increment);
        }
        return this;
    }

    @Override
    default ReferenceCounted touch() {
        ByteBuf data = data();
        if (data !=null){
            data.touch();
        }
        return this;
    }

    @Override
    default ReferenceCounted touch(Object hint) {
        ByteBuf data = data();
        if (data !=null){
            data.touch(hint);
        }
        return this;
    }

    @Override
    default boolean release() {
        ByteBuf data = data();
        if (data !=null){
            return data.release();
        }
        return false;
    }

    @Override
    default boolean release(int decrement) {
        ByteBuf data = data();
        if (data !=null){
            return data.release(decrement);
        }
        return false;
    }
}
