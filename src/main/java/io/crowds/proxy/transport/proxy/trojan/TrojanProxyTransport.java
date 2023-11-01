package io.crowds.proxy.transport.proxy.trojan;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.TP;
import io.crowds.proxy.common.HandlerName;
import io.crowds.proxy.transport.Destination;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.TlsOption;
import io.crowds.proxy.transport.proxy.FullConeProxyTransport;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;
import org.bouncycastle.crypto.digests.SHA224Digest;
import org.bouncycastle.jcajce.provider.digest.SHA224;

import java.util.HexFormat;

public class TrojanProxyTransport extends FullConeProxyTransport {
    private TrojanOption trojanOption;


    public TrojanProxyTransport(ChannelCreator channelCreator, TrojanOption trojanOption) {
        super(channelCreator, trojanOption.setTls(trojanOption.getTls()!=null?trojanOption.getTls():new TlsOption().setEnable(true)));
        this.trojanOption=trojanOption;

    }

    @Override
    public String getTag() {
        return trojanOption.getName();
    }

    @Override
    protected Destination getRemote(TP tp) {
        return new Destination(NetAddr.of(trojanOption.getAddress()),TP.TCP);
    }

    @Override
    protected Future<Channel> proxy(Channel channel, NetLocation netLocation) {
        HandlerName baseName = handlerName();
        channel.pipeline()
                .addLast(baseName.with("codec"),new TrojanCodec(netLocation.getTp()))
                .addLast(baseName.with("handler"),new TrojanHandler(trojanOption,netLocation));

        return channel.eventLoop().newSucceededFuture(channel);
    }
}
