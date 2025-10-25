package io.crowds.lib.windows;

import top.dreamlike.panama.generator.annotation.NativeFunction;

import java.lang.foreign.MemorySegment;

public interface WinFFI {

    @NativeFunction(needErrorNo = true)
    int setsockopt(int s, int level, int optname, MemorySegment optval, int optlen);

    default int setsockopt(int s, int level, int optname, MemorySegment optval){
        return setsockopt(s,level,optname,optval,(int)optval.byteSize());
    }

}
