package io.crowds.proxy.transport.vmess.stream;

import io.netty.channel.ChannelFuture;

import java.net.URISyntaxException;

public interface StreamCreator {


    ChannelFuture create() throws Exception;

}
