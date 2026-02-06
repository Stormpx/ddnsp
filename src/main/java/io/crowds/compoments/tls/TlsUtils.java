package io.crowds.compoments.tls;

import io.crowds.Ddnsp;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslHandler;
import io.netty.internal.tcnative.SSL;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;

public class TlsUtils {
    private static final MethodHandle INTERNAL_BUFFER_MH;
    private static final MethodHandle GET_QUIC_CONNECTION_SSL_MH;


    static {
        Class<SslHandler> klass = SslHandler.class;
        try {
            INTERNAL_BUFFER_MH = Ddnsp.fetchMethodHandlesLookup0()
                                      .findSpecial(klass, "internalBuffer", MethodType.methodType(ByteBuf.class), klass);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
    static {
        try {
            Class<?> QuicheQuicChannelClass = Class.forName("io.netty.handler.codec.quic.QuicheQuicChannel");
            Class<?> QuicheQuicConnectionClass = Class.forName("io.netty.handler.codec.quic.QuicheQuicConnection");
            VarHandle connectionVh = Ddnsp.fetchMethodHandlesLookup0().findVarHandle(QuicheQuicChannelClass, "connection", QuicheQuicConnectionClass);
            VarHandle sslVh = Ddnsp.fetchMethodHandlesLookup0().findVarHandle(QuicheQuicConnectionClass,"ssl",long.class);

            GET_QUIC_CONNECTION_SSL_MH = MethodHandles.filterReturnValue(connectionVh.toMethodHandle(VarHandle.AccessMode.GET),sslVh.toMethodHandle(VarHandle.AccessMode.GET));
        } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

    }


    public static ByteBuf internalBuffer(SslHandler sslHandler) throws Throwable {
        return (ByteBuf) INTERNAL_BUFFER_MH.invoke(sslHandler);
    }

    public static byte[] exportKeyingMaterial(long ssl,byte[] label,byte[] context,int olen) {
        try {
            OpenSsl.ensureAvailability();
            return SSL.exportKeyingMaterial(ssl,label,context,olen);
        } catch (Exception e) {
            throw new RuntimeException(SSL.getLastError());
        }
    }

    public static byte[] exportKeyingMaterial(QuicChannel quicChannel,byte[] label,byte[] context,int olen) {
        try {
            long ssl = (long) GET_QUIC_CONNECTION_SSL_MH.invoke(quicChannel);
            return exportKeyingMaterial(ssl,label,context, olen);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

    }

}
