// Generated by jextract

package io.crowds.lib.boringtun;

import java.lang.foreign.*;

public class x25519_key {

    static final  GroupLayout $struct$LAYOUT = MemoryLayout.structLayout(
        MemoryLayout.sequenceLayout(32, Constants$root.C_CHAR$LAYOUT).withName("key")
    ).withName("x25519_key");
    public static MemoryLayout $LAYOUT() {
        return x25519_key.$struct$LAYOUT;
    }
    public static MemorySegment key$slice(MemorySegment seg) {
        return seg.asSlice(0, 32);
    }
    public static long sizeof() { return $LAYOUT().byteSize(); }
    public static MemorySegment allocate(SegmentAllocator allocator) { return allocator.allocate($LAYOUT()); }
    public static MemorySegment allocateArray(int len, SegmentAllocator allocator) {
        return allocator.allocate(MemoryLayout.sequenceLayout(len, $LAYOUT()));
    }
    public static MemorySegment ofAddress(MemoryAddress addr, MemorySession session) { return RuntimeHelper.asArray(addr, $LAYOUT(), 1, session); }
}

