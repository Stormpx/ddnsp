package io.crowds.proxy.common;

import io.crowds.lib.unix.Unix;
import io.crowds.lib.windows.Windows;
import io.crowds.util.Reflect;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.AbstractNioChannel;
import io.netty.channel.unix.UnixChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.internal.PlatformDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.util.function.BiConsumer;


public class BaseChannelInitializer extends ChannelInitializer<Channel> {
    private static final Logger logger = LoggerFactory.getLogger(BaseChannelInitializer.class);

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

    private void bindToDevice(Channel ch){
        try {
            if (ch instanceof UnixChannel unixChannel&&unixChannel.fd().isOpen()){
                int fd = unixChannel.fd().intValue();
                Unix.bindToDevice(fd,device);
            }else if (ch instanceof AbstractNioChannel nioChannel){
                int fd = Reflect.getFd(nioChannel);
                if (PlatformDependent.isWindows()){
                    Windows.bindToDevice(fd,device);
                }else{
                    Unix.bindToDevice(fd,device);
                }
            }
        } catch (Throwable e) {
            logger.error("",e);
        }
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        if (this.device!=null){
            bindToDevice(ch);
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
