package io.crowds.proxy.common;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.IdleStateHandler;

import javax.net.ssl.SSLException;


public class BaseChannelInitializer extends ChannelInitializer<Channel> {

    private SslContext sslContext;
    private String tlsServerName;
    private int port;

    private Integer connIdle;
    private ChannelInitializer<Channel> subInitializer;

    public BaseChannelInitializer tls(boolean tls,boolean allowInsecure,String serverName,int port) throws SSLException {
        if (tls){
            var builder= SslContextBuilder.forClient();
            if (allowInsecure)
                builder.trustManager(InsecureTrustManagerFactory.INSTANCE);
            this.sslContext= builder.build();
            this.tlsServerName=serverName;
            this.port=port;
        }else {
            this.sslContext=null;
        }
        return this;
    }

    public BaseChannelInitializer connIdle(int idle){
        this.connIdle=idle;
        return this;
    }

    public BaseChannelInitializer initializer(ChannelInitializer<Channel>  initializer){
        this.subInitializer=initializer;
        return this;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        if (sslContext!=null){
            ch.pipeline().addLast("tls",sslContext.newHandler(ch.alloc(),this.tlsServerName,this.port));
        }
        if (this.subInitializer!=null){
            ch.pipeline().addLast(this.subInitializer);
        }
        if (connIdle!=null){
            ch.pipeline().addLast(new IdleStateHandler(0,0, connIdle));
        }
    }
}
