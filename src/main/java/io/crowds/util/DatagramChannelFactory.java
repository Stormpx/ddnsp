package io.crowds.util;

import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;

import java.util.function.Function;
import java.util.function.Supplier;

public interface DatagramChannelFactory<T extends DatagramChannel> extends io.netty.channel.ChannelFactory<T> {

    default T newChannel(AddrType addrType){
        return newChannel(addrType.toNettyFamily());
    }

    T newChannel(InternetProtocolFamily family);

    static <T extends DatagramChannel> DatagramChannelFactory<T> newFactory(Function<InternetProtocolFamily,T> newFamilyChannel, Supplier<T> newChannel){
        return new DatagramChannelFactory(){

            @Override
            public T newChannel() {
                return newChannel.get();
            }

            @Override
            public T newChannel(InternetProtocolFamily family) {
                return newFamilyChannel.apply(family);
            }
        };
    }
}
