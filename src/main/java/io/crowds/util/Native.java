package io.crowds.util;

import top.dreamlike.panama.generator.proxy.NativeArray;
import top.dreamlike.panama.generator.proxy.NativeCallGenerator;
import top.dreamlike.panama.generator.proxy.StructProxyGenerator;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;

public class Native {
    public final static StructProxyGenerator STRUCT_PROXY_GENERATOR = new StructProxyGenerator();
    public final static NativeCallGenerator NATIVE_CALL_GENERATOR = new NativeCallGenerator(STRUCT_PROXY_GENERATOR);
    static {
        NATIVE_CALL_GENERATOR.plainMode();
    }

    public static <T> T nativeGenerate(Class<T> klass){
        return NATIVE_CALL_GENERATOR.generate(klass);
    }

    public static <T> T structAlloc(Arena arena, Class<T> klass){
        MemoryLayout layout = STRUCT_PROXY_GENERATOR.extract(klass);
        MemorySegment segment = arena.allocate(layout);
        return STRUCT_PROXY_GENERATOR.enhance(klass,segment);
    }

    public static <T> NativeArray<T> structArrayAlloc(Arena arena, Class<T> klass, long count){
        MemoryLayout layout = getLayout(klass);
        MemorySegment segment = arena.allocate(layout, count);
        return new NativeArray<>(STRUCT_PROXY_GENERATOR,segment,klass);
    }

    public static MemoryLayout getLayout(Class<?> klass){
        return STRUCT_PROXY_GENERATOR.extract(klass);
    }

    public static  <T> T as(MemorySegment segment, Class<T> klass){
        return STRUCT_PROXY_GENERATOR.enhance(klass,segment);
    }

}
