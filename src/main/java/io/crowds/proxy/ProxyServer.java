package io.crowds.proxy;

import io.crowds.dns.DnsContext;
import io.crowds.proxy.services.http.HttpServer;
import io.crowds.proxy.services.socks.SocksServer;
import io.crowds.proxy.services.transparent.TransparentServer;
import io.crowds.tun.TunOption;
import io.crowds.tun.TunService;
import io.crowds.tun.wireguard.WireGuardOption;
import io.crowds.tun.wireguard.WireGuardTunService;
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

    private List<TunService> tunServices;

    public ProxyServer(EventLoopGroup eventLoopGroup) {
        this(eventLoopGroup,eventLoopGroup);
    }
    public ProxyServer(EventLoopGroup acceptor,EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        this.axis=new Axis(acceptor,eventLoopGroup);
    }

    public ProxyServer setProxyOption(ProxyOption proxyOption) {
        this.proxyOption = proxyOption;
        this.axis.setProxyOption(proxyOption);
        return this;
    }

    private Future<Void> startTun(TunOption tunOption){
        WireGuardOption wireGuardOption= (WireGuardOption) tunOption;
        WireGuardTunService service = new WireGuardTunService(eventLoopGroup, wireGuardOption);
        return service.start();
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
        if (proxyOption.getTuns()!=null){
            proxyOption.getTuns().stream().map(this::startTun).forEach(futures::add);
        }

        return CompositeFuture.all(futures)
                .map((Void)null);
    }

    public Handler<DnsContext> getDnsHandler(){
        return this.axis.getFakeDns();
    }


}
