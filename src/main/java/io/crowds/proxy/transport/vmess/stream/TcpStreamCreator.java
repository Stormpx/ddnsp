package io.crowds.proxy.transport.vmess.stream;

import io.crowds.proxy.ChannelCreator;
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

import javax.net.ssl.TrustManagerFactory;
import java.net.InetSocketAddress;

public class TcpStreamCreator implements StreamCreator {

    protected VmessOption vmessOption;
    protected ChannelCreator channelCreator;

    public TcpStreamCreator(VmessOption vmessOption, ChannelCreator channelCreator) {
        this.vmessOption = vmessOption;
        this.channelCreator = channelCreator;
    }


    @Override
    public ChannelFuture create() throws Exception {
        boolean tls = vmessOption.isTls();
        final SslContext sslCtx;

        if (tls) {
            var builder=SslContextBuilder.forClient();
            if (vmessOption.isTlsAllowInsecure())
                builder.trustManager(InsecureTrustManagerFactory.INSTANCE);
            sslCtx= builder.build();
        }else{
            sslCtx=null;
        }
        InetSocketAddress address = vmessOption.getAddress();
        var serverName=vmessOption.getTlsServerName();
        var cf=channelCreator.createTcpChannel(address, new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                if (sslCtx!=null)
                    ch.pipeline().addLast("ssl",sslCtx.newHandler(ch.alloc(),serverName!=null?serverName:vmessOption.getAddress().getHostString(),address.getPort()));
            }
        });

        return cf;
    }
}
