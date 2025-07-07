package io.crowds.util;

import io.crowds.Ddnsp;
import io.netty.channel.nio.AbstractNioChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;

public class Reflect {
    private final static Logger logger= LoggerFactory.getLogger(Reflect.class);

    private final static Class<?> SELCHIMPL_CLASS;
    private final static MethodHandle SELCHIMPL_GETFDVAL;

    private final static MethodHandle NIOCHANNEL_JAVACHANNEL;

    static {
        Class<?> selchimplClass=null;
        MethodHandle getFDVal=null;
        try {
            selchimplClass = Class.forName("sun.nio.ch.SelChImpl");
            getFDVal = Ddnsp.fetchMethodHandlesLookup0().findVirtual(selchimplClass, "getFDVal", MethodType.methodType(int.class));
        } catch (Throwable ignore) {
        }
        SELCHIMPL_CLASS = selchimplClass;
        SELCHIMPL_GETFDVAL = getFDVal;

        MethodHandle nioJavaChannel=null;
        try {
            var klass = AbstractNioChannel.class;
            nioJavaChannel = Ddnsp.fetchMethodHandlesLookup0().findVirtual(klass,"javaChannel",MethodType.methodType(SelectableChannel.class));
        } catch (NoSuchMethodException | IllegalAccessException ignore) {

        }
        NIOCHANNEL_JAVACHANNEL=nioJavaChannel;
    }

    public static int getFd(AbstractNioChannel nioChannel) throws Throwable {
        if (NIOCHANNEL_JAVACHANNEL==null){
            throw new RuntimeException("Unable get fd from AbstractNioChannel");
        }
        return getFd((SelectableChannel)NIOCHANNEL_JAVACHANNEL.invoke(nioChannel));
    }

    public static int getFd(Channel channel) throws Throwable {
        if (SELCHIMPL_GETFDVAL==null){
            throw new RuntimeException("Unable get fd from Channel");
        }
        Class<?> klass = SELCHIMPL_CLASS;
        if (klass==null){
            throw new IllegalArgumentException("Class sun.nio.ch.SelChImpl not found");
        }
        if (!klass.isInstance(channel)){
            throw new IllegalArgumentException("Channel is not inherit SelChImpl");
        }
        return (int) SELCHIMPL_GETFDVAL.invoke(channel);
    }


}
