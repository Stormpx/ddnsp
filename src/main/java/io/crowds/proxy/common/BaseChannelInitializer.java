package io.crowds.proxy.common;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import javax.net.ssl.SSLException;
import java.util.function.Consumer;


public class BaseChannelInitializer extends ChannelInitializer<Channel> {

    private SslContext sslContext;
    private String tlsServerName;
    private int port;

    private Integer connIdle;
    private Consumer<IdleStateEvent> idleEventHandler;
    private ChannelInitializer<Channel> subInitializer;
    private HandlerConfigurer configurer;

    private LogLevel logLevel;

    public BaseChannelInitializer() {
    }




    public BaseChannelInitializer tls(boolean tls, boolean allowInsecure, String serverName, int port) throws SSLException {
        if (tls){
            var builder= SslContextBuilder.forClient().sslProvider(SslProvider.OPENSSL);
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

    public BaseChannelInitializer connIdle(int idle,Consumer<IdleStateEvent> idleEventHandler){
        this.connIdle=idle;
        this.idleEventHandler=idleEventHandler;
        return this;
    }

    public BaseChannelInitializer configurer(HandlerConfigurer configurer) {
        this.configurer = configurer;
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
        if (logLevel!=null) {
            ch.pipeline().addLast(new LoggingHandler(logLevel));
        }
        if (this.subInitializer!=null){
            ch.pipeline().addLast(this.subInitializer);
        }
        if (this.configurer!=null){
            ch.pipeline().addLast(this.configurer);
        }
        if (connIdle!=null&&connIdle>0){
            ch.pipeline().addLast(new IdleTimeoutHandler(connIdle,this.idleEventHandler));
        }
    }

    public BaseChannelInitializer logLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
        return this;
    }
}
