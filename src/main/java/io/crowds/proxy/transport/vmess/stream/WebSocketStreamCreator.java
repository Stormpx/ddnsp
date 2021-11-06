package io.crowds.proxy.transport.vmess.stream;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.transport.vmess.VmessOption;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;

import java.net.InetSocketAddress;
import java.net.URI;

public class WebSocketStreamCreator extends TcpStreamCreator{


    public WebSocketStreamCreator(VmessOption vmessOption, ChannelCreator channelCreator) {
        super(vmessOption, channelCreator);
    }

    @Override
    public ChannelFuture create() throws Exception {
        VmessOption.WsOption ws = vmessOption.getWs();
        InetSocketAddress address = vmessOption.getAddress();
        boolean tls = vmessOption.isTls();
        var uri=new URI(!tls?"ws":"wss",null, address.getHostString(), address.getPort(), ws.getPath(),null,null);
        var maskHandler=new WebsocketMaskHandler(
                WebSocketClientHandshakerFactory.newHandshaker(
                        uri, WebSocketVersion.V13,null,true,ws.getHeaders()!=null?ws.getHeaders():new DefaultHttpHeaders()));

        ChannelFuture cf = super.create();
        cf.channel().pipeline().addLast(
                new HttpClientCodec(),
                new HttpObjectAggregator(8192),
                WebSocketClientCompressionHandler.INSTANCE,
                maskHandler);
        cf.addListener(future -> {
            if (!future.isSuccess()){
                maskHandler.handshakePromise().tryFailure(future.cause());
                return;
            }
            maskHandler.handshake();
        });



        return maskHandler.handshakePromise();
    }
}
