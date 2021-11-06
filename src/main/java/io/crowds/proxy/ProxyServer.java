package io.crowds.proxy;

import io.crowds.proxy.services.socks.SocksServer;
import io.crowds.proxy.services.transparent.TransparentServer;
import io.netty.channel.*;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ProxyServer {
    private final static Logger logger= LoggerFactory.getLogger(ProxyServer.class);

    private ProxyOption proxyOption;
    private EventLoopGroup eventLoopGroup;

    private Axis axis;

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
        if (proxyOption.getSocks()!=null){
            this.socksServer = new SocksServer(proxyOption.getSocks(),this.axis);
            futures.add(socksServer.start());
        }
        if (proxyOption.getTransparent()!=null){
            this.transparentServer=new TransparentServer(proxyOption.getTransparent(),axis);
            futures.add(transparentServer.start());
        }

        return CompositeFuture.any(futures)
                .map((Void)null);
    }




}
