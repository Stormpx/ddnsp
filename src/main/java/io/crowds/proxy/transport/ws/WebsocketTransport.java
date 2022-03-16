package io.crowds.proxy.transport.ws;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.NetAddr;
import io.crowds.proxy.TP;
import io.crowds.proxy.transport.Destination;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.DirectTransport;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

import java.net.URI;
import java.util.Objects;

public class WebsocketTransport extends DirectTransport {
    private WsOption wsOption;

    public WebsocketTransport(ProtocolOption protocolOption, ChannelCreator channelCreator) {
        super(protocolOption, channelCreator);
        this.wsOption=protocolOption.getTransport().getWs();
        Objects.requireNonNull(wsOption);
    }

    @Override
    public Future<Channel> createChannel(EventLoop eventLoop, Destination dest) throws Exception {
        Future<Channel> future = super.createChannel(eventLoop, dest);
        TP tp = dest.tp();
        NetAddr addr = dest.addr();
        if (tp==TP.UDP)
            return future;

        boolean tls=protocolOption.getTls()!=null&&protocolOption.getTls().isEnable();

        var uri=new URI(!tls?"ws":"wss",null, addr.getHost(), addr.getPort(), wsOption.getPath(),null,null);
        var maskHandler=new WebsocketMaskHandler(
                WebSocketClientHandshakerFactory.newHandshaker(
                        uri, WebSocketVersion.V13,null,true,wsOption.getHeaders()!=null?wsOption.getHeaders():new DefaultHttpHeaders()));
        Promise<Channel> promise = eventLoop.newPromise();
        future.addListener((FutureListener<Channel>) f->{
            if (!f.isSuccess()){
                promise.tryFailure(f.cause());
                return;
            }
            var cf = maskHandler.handshake(f.get());
            cf.addListener(f1->{
               if (!f1.isSuccess()){
                   promise.tryFailure(f1.cause());
                   return;
               }
               promise.trySuccess(cf.channel());
            });
        });

        return promise;
    }
}
