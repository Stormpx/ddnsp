package io.crowds.proxy.transport.vmess.stream;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.common.BaseChannelInitializer;
import io.crowds.proxy.transport.vmess.VmessEndPoint;
import io.crowds.proxy.transport.vmess.VmessMessageCodec;
import io.crowds.proxy.transport.vmess.VmessOption;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.IdleStateHandler;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetSocketAddress;

public class TcpStreamCreator implements StreamCreator {

    protected VmessOption vmessOption;
    protected ChannelCreator channelCreator;

    public TcpStreamCreator(VmessOption vmessOption, ChannelCreator channelCreator) {
        this.vmessOption = vmessOption;
        this.channelCreator = channelCreator;
    }

    public ChannelFuture create0(ChannelInitializer<Channel> initializer) throws SSLException {
        var base=new BaseChannelInitializer();
        var address = vmessOption.getAddress();
        var tls = vmessOption.isTls();
        var serverName=vmessOption.getTlsServerName();

        base.tls(tls,vmessOption.isTlsAllowInsecure(),serverName,address.getPort());
        if (vmessOption.getConnIdle()!=0){
            base.connIdle(vmessOption.getConnIdle());
        }
        if (initializer!=null){
            base.initializer(initializer);
        }

        return channelCreator.createTcpChannel(address,base);

    }

    @Override
    public ChannelFuture create() throws Exception {
        return create0(null);
    }
}
