package io.crowds.proxy;

import io.crowds.Context;
import io.crowds.dns.server.DnsContext0;
import io.crowds.proxy.services.http.HttpServer;
import io.crowds.proxy.services.socks.SocksServer;
import io.crowds.proxy.services.transparent.TransparentServer;
import io.crowds.proxy.services.tun.TunServer;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ProxyServer {
    private final static Logger logger= LoggerFactory.getLogger(ProxyServer.class);

    private ProxyOption proxyOption;
    private final Context context;
    private final Axis axis;


    private HttpServer httpServer;
    private SocksServer socksServer;
    private TransparentServer transparentServer;
    private TunServer tunServer;


    public ProxyServer(Context context) {
        this.context = context;
        this.axis=new Axis(context);
    }

    public ProxyServer setProxyOption(ProxyOption proxyOption) {
        this.proxyOption = proxyOption;
        this.axis.setProxyOption(proxyOption);
        return this;
    }


    public Future<Void> start(){
        List<Future<?>> futures=new ArrayList<>();
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
        if (proxyOption.getTun()!=null&&proxyOption.getTun().isEnable()){
            this.tunServer = new TunServer(proxyOption.getTun(), axis);
            futures.add(tunServer.start());
        }

        return Future.all(futures)
                .map((Void)null);
    }

    public Handler<DnsContext0> getDnsHandler(){
        return this.axis.getFakeDns();
    }


}
