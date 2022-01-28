package io.crowds.proxy;

import io.crowds.dns.DnsContext;
import io.crowds.proxy.services.http.HttpServer;
import io.crowds.proxy.services.socks.SocksServer;
import io.crowds.proxy.services.transparent.TransparentServer;
import io.netty.channel.*;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ProxyServer {
    private final static Logger logger= LoggerFactory.getLogger(ProxyServer.class);

    private ProxyOption proxyOption;
    private EventLoopGroup eventLoopGroup;

    private Axis axis;


    private HttpServer httpServer;
    private SocksServer socksServer;
    private TransparentServer transparentServer;

    public ProxyServer(EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        this.axis=new Axis(eventLoopGroup);
    }

    public ProxyServer setProxyOption(ProxyOption proxyOption) {
        this.proxyOption = proxyOption;
        this.axis.setProxyOption(proxyOption);
        return this;
    }


    public Future<Void> start(){
        List<Future> futures=new ArrayList<>();
        if (proxyOption.getHttp()!=null&&proxyOption.getHttp().isEnable()){
            this.httpServer=new HttpServer(proxyOption.getHttp(),this.axis);
            futures.add(httpServer.start());
        }
        if (proxyOption.getSocks()!=null&&proxyOption.getSocks().isEnable()){
            this.socksServer = new SocksServer(proxyOption.getSocks(),this.axis);
            futures.add(socksServer.start());
        }
        if (proxyOption.getTransparent()!=null&&proxyOption.getTransparent().isEnable()){
            this.transparentServer=new TransparentServer(proxyOption.getTransparent(),axis);
            futures.add(transparentServer.start());
        }

        return CompositeFuture.any(futures)
                .map((Void)null);
    }

    public Handler<DnsContext> getFakeDnsHandler(){
        return this.axis.getFakeDns();
    }


}
