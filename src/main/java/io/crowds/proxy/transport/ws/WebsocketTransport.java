package io.crowds.proxy.transport.ws;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.NetAddr;
import io.crowds.proxy.TP;
import io.crowds.proxy.transport.Destination;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.DirectTransport;
import io.crowds.proxy.transport.Transport;
import io.crowds.util.AddrType;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

public class WebsocketTransport extends DirectTransport {
    private WsOption wsOption;

    public WebsocketTransport(ProtocolOption protocolOption, ChannelCreator channelCreator) {
        super(protocolOption, channelCreator);
        this.wsOption=protocolOption.getTransport().getWs();
        Objects.requireNonNull(wsOption);
    }

    private WebsocketMaskHandler newHandler(NetAddr addr) throws URISyntaxException {

        boolean tls=protocolOption.getTls()!=null&&protocolOption.getTls().isEnable();

        var uri=new URI(!tls?"ws":"wss",null, addr.getHost(), addr.getPort(), wsOption.getPath(),null,null);

        return new WebsocketMaskHandler(
                WebSocketClientHandshakerFactory.newHandshaker(
                        uri, WebSocketVersion.V13,null,true,wsOption.getHeaders()!=null?wsOption.getHeaders():new DefaultHttpHeaders()));
    }

    @Override
    public Future<Channel> openChannel(EventLoop eventLoop, Destination dest, AddrType preferType, Transport delegate) throws Exception {
        Future<Channel> future = super.openChannel(eventLoop, dest,preferType,delegate);
        if (dest.tp()==TP.UDP)
            return future;

        var maskHandler=newHandler(dest.addr());

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
