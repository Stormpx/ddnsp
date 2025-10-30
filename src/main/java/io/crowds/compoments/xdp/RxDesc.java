package io.crowds.compoments.xdp;

import java.lang.foreign.MemorySegment;

public record RxDesc(MemorySegment data, long offset, long len) {
}
