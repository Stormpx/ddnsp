package io.crowds.compoments.tls;

import io.crowds.Ddnsp;
import io.netty.buffer.ByteBuf;
import io.netty.handler.ssl.SslHandler;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

public class TlsUtils {
    private static final MethodHandle INTERNAL_BUFFER_MH;

    static {
        Class<SslHandler> klass = SslHandler.class;
        try {
            INTERNAL_BUFFER_MH = Ddnsp.fetchMethodHandlesLookup0()
                                      .findSpecial(klass, "internalBuffer", MethodType.methodType(ByteBuf.class), klass);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }



    public static ByteBuf internalBuffer(SslHandler sslHandler) throws Throwable {
        return (ByteBuf) INTERNAL_BUFFER_MH.invoke(sslHandler);
    }


}
