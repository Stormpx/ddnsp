package io.crowds.lib.xdp;

import io.crowds.lib.xdp.ffi.LibBpf;
import io.crowds.util.Native;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public record BpfRingBuffer(int mapFd,MemorySegment rb,MemorySegment entryPoint) {
    private final static MethodHandle CALLBACK_MH;
    private final static MethodHandle REINTERPRET_MH;
    private final static MethodHandle TRANSFERDATA_MH;
    private final static MethodHandle MAPRETURNVAL_MH;
    static {
        try {

            var lambdaFn = MethodHandles.lookup()
                                        .findVirtual(RingBufferCallback.class, "handle",
                                                MethodType.methodType(boolean.class, Object.class));
            var reinterpret_mh = MethodHandles.lookup()
                                              .findStatic(BpfRingBuffer.class, "reinterpret",
                                                      MethodType.methodType(MemorySegment.class, MemorySegment.class, long.class));
            var extractData_mh = MethodHandles.lookup()
                                              .findStatic(BpfRingBuffer.class, "transferData",
                                                      MethodType.methodType(Object.class, Class.class ,MemorySegment.class));
            var mapRetVal_mh = MethodHandles.lookup()
                                            .findStatic(BpfRingBuffer.class, "mapRetVal",
                                                    MethodType.methodType(int.class,boolean.class));
            CALLBACK_MH = lambdaFn;
            REINTERPRET_MH = reinterpret_mh;
            TRANSFERDATA_MH = extractData_mh;
            MAPRETURNVAL_MH = mapRetVal_mh;

        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public interface RingBufferCallback<T>{

        boolean handle(T data);

    }

    static MemorySegment reinterpret(MemorySegment data,long len){
        return data.reinterpret(len);
    }

    static Object transferData(Class<?> klass, MemorySegment data){
        if (klass == MemorySegment.class){
            return data;
        }
        if (klass.isPrimitive()){
            MemoryLayout layout = Native.getLayout(klass);
            return layout.varHandle().get(data,0);
        }
        if (klass.isAssignableFrom(String.class)){
            return data.getString(0);
        }
        return Native.as(data,klass);
    }

    static int mapRetVal(boolean success){
        return success?0:-1;
    }

    public static <T> MethodHandle genMethodHandle(Class<T> klass, RingBufferCallback<T> cb){
        MethodHandle methodHandle = CALLBACK_MH.asType(CALLBACK_MH.type().changeParameterType(1,klass)).bindTo(cb);
        methodHandle = MethodHandles.dropArguments(methodHandle,0, MemorySegment.class);
        methodHandle = MethodHandles.dropArguments(methodHandle,2, MemorySegment.class,long.class);
        methodHandle = MethodHandles.filterArguments(methodHandle,1,
                MethodHandles.insertArguments(TRANSFERDATA_MH.asType(TRANSFERDATA_MH.type().changeReturnType(klass)),0,klass));
        methodHandle = MethodHandles.foldArguments(methodHandle,1,REINTERPRET_MH);
        methodHandle = MethodHandles.filterReturnValue(methodHandle,MAPRETURNVAL_MH);
        return methodHandle;
    }

    public static <T> MemorySegment genEntryPoint(Class<T> klass,RingBufferCallback<T> cb){
        Arena arena = Arena.global();
        return Linker.nativeLinker()
                     .upcallStub(genMethodHandle(klass,cb),
                             FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                     ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG),
                             arena);
    }



    public int fd(){
        return LibBpf.INSTANCE.ring_buffer__epoll_fd(rb);
    }

    public int consume(){
        return LibBpf.INSTANCE.ring_buffer__consume(rb);
    }

    public void free(){
        LibBpf.INSTANCE.ring_buffer__free(rb);
    }
}
