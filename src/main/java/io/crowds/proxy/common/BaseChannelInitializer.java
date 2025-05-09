package io.crowds.proxy.common;

import io.crowds.lib.unix.Unix;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.epoll.AbstractEpollServerChannel;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.UnixChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import javax.net.ssl.SSLException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;


public class BaseChannelInitializer extends ChannelInitializer<Channel> {

    public final static ChannelInitializer<Channel> EMPTY = new ChannelInitializer<Channel>() {
        @Override
        protected void initChannel(Channel ch) throws Exception {

        }
    };

    private SslContext sslContext;
    private String tlsServerName;
    private int port;

    private Integer connIdle;
    private BiConsumer<Channel,IdleStateEvent> idleEventHandler;
    private ChannelInitializer<Channel> subInitializer;
    private HandlerConfigurer configurer;

    private String device;

    private LogLevel logLevel;

    public BaseChannelInitializer() {
    }




    public BaseChannelInitializer tls(boolean tls, boolean allowInsecure, String serverName, int port) throws SSLException {
        if (tls){
            var builder= SslContextBuilder.forClient().sslProvider(OpenSsl.isAvailable()?SslProvider.OPENSSL:SslProvider.JDK);
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

    public BaseChannelInitializer connIdle(int idle, BiConsumer<Channel,IdleStateEvent> idleEventHandler){
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

    public BaseChannelInitializer bindToDevice(String device) {
        this.device = device;
        return this;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        if (this.device!=null){
            if (ch instanceof UnixChannel unixChannel&&unixChannel.fd().isOpen()){
                int fd = unixChannel.fd().intValue();
                Unix.bindToDevice(fd,device);
            }
        }
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
