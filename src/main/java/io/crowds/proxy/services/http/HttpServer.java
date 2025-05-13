package io.crowds.proxy.services.http;

import io.crowds.Context;
import io.crowds.proxy.Axis;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.unix.UnixChannelOption;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.*;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.impl.Http1xOrH2CHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.util.Objects;

public class HttpServer {
    private final static Logger logger= LoggerFactory.getLogger(HttpServer.class);

    private final Axis axis;
    private final HttpOption httpOption;

    public HttpServer( HttpOption httpOption,Axis axis) {
        Objects.requireNonNull(axis);
        Objects.requireNonNull(httpOption);
        this.axis = axis;
        this.httpOption = httpOption;
    }

    public Future<Void> start(){
        Promise<Void> promise=Promise.promise();
        InetSocketAddress socketAddress = new InetSocketAddress(httpOption.getHost(), httpOption.getPort());
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        Context context = axis.getContext();
        ServerBootstrap bootstrap = serverBootstrap.group(context.getAcceptor(), context.getEventLoopGroup())
                .channelFactory(context.getServerChannelFactory());
        if (Epoll.isAvailable()){
            bootstrap.option(UnixChannelOption.SO_REUSEPORT,true);
        }

        final SslContext sslCtx;
        if (httpOption.isTls()){
            try {
                SslProvider provider = OpenSsl.isAvailable() ? SslProvider.OPENSSL : SslProvider.JDK;
                sslCtx = SslContextBuilder.forServer(httpOption.getCert().toFile(),httpOption.getKey().toFile(),httpOption.getKeyPassword())
                                          .sslProvider(provider)
                                          .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                                          .applicationProtocolConfig(new ApplicationProtocolConfig(
                                                  ApplicationProtocolConfig.Protocol.ALPN,
                                                  // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                                                  ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                                                  // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                                                  ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                                                  ApplicationProtocolNames.HTTP_2,
                                                  ApplicationProtocolNames.HTTP_1_1))
                                          .build();
            } catch (SSLException e) {
                promise.fail(e);
                return promise.future();
            }
        }else{
            sslCtx = null;
        }

        bootstrap
                .option(ChannelOption.SO_REUSEADDR,true)
                .childOption(ChannelOption.SO_REUSEADDR,true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        if (sslCtx!=null){
                            ch.pipeline()
                              .addLast(sslCtx.newHandler(ch.alloc()))
                              .addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1){
                                  @Override
                                  protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
                                      if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                                          ch.pipeline().addLast(new Http2ServerHandler(httpOption,axis));
                                      } else if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
                                          ch.pipeline().addLast(new Http1ServerHandler(httpOption,axis));
                                      }else{
                                          throw new IllegalStateException("unknown protocol: " + protocol);
                                      }

                                  }
                              });
                        }else{
                            ch.pipeline().addLast(new Http1xOrH2CHandler(){
                                @Override
                                protected void configure(ChannelHandlerContext ctx, boolean h2c) {
                                    if (!h2c){
                                        ch.pipeline().addLast(new Http1ServerHandler(httpOption,axis));
                                    }else{
                                        ch.pipeline().addLast(new Http2ServerHandler(httpOption,axis));
                                    }
                                }
                            });
                        }
                    }
                })
                .bind(socketAddress)
                .addListener(future -> {
                    if (future.isSuccess()) {
                        promise.complete();
                        logger.info("start http{} proxy server {}", sslCtx==null?"":"s",socketAddress);
                    }else {
                        future.cause().printStackTrace();
                        promise.tryFail(future.cause());
                        logger.error("start http proxy server failed cause:{}", future.cause().getMessage());
                    }
                });
        return promise.future();
    }

}
