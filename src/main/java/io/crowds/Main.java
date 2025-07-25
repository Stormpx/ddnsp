package io.crowds;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.crowds.ddns.Ddns;
import io.crowds.dns.DnsClient;
import io.crowds.dns.DnsOption;
import io.crowds.dns.DnsServer;
import io.crowds.proxy.ProxyOption;
import io.crowds.proxy.ProxyServer;
import io.crowds.util.Mmdb;
import io.crowds.util.Strs;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.TimeUnit;


public class Main {

    private static String mmdbTarget;

    private static String getConfigPath(String[] args){
        String configFile = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-c", "--config"->{
                    if (i+1 < args.length) {
                        configFile = args[i + 1];
                    }
                }
            }
        }
        return configFile;
    }

    public static void main(String[] args) throws InterruptedException {
//        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
        long start = System.nanoTime();
        System.getProperties().setProperty("vertx.disableDnsResolver","true");
        Context context = Ddnsp.newContext(Ddnsp::dnsResolver);
        Vertx  vertx = context.getVertx();

        String configFile=getConfigPath(args);

        DdnspOptionLoader loader = new DdnspOptionLoader(vertx);

        if (configFile != null) {
           loader.setFilePath(configFile);
        }
        loader.load()
                .compose(option->{
                    ProxyOption proxyOption = option.getProxy();
                    DnsOption dnsOption = option.getDns();
                    var dnsClient=new DnsClient(context, dnsOption.genClientOption());
                    Ddnsp.initDnsResolver(dnsClient);
                    InetSocketAddress socketAddress = new InetSocketAddress(dnsOption.getHost(), dnsOption.getPort());
                    DnsServer dnsServer = new DnsServer(context,dnsClient).setOption(dnsOption);
                    Ddns ddns = new Ddns(vertx, option.getDdns());
                    Future<Void> dnsFuture = dnsServer.start(socketAddress);
                    ProxyServer proxyServer = new ProxyServer(context)
                            .setProxyOption(proxyOption);
                    Future<Void> proxyFuture = proxyServer.start()
                            .onSuccess(v->{
                                if (proxyServer.getDnsHandler()!=null){
                                    dnsServer.dnsContextHandler(proxyServer.getDnsHandler());
                                }
                            });

                    loader.optionChangeHandler(it -> {
                        if (!Strs.isBlank(it.getLogLevel())){
                            setLoggerLevel(Level.toLevel(it.getLogLevel()));
                        }
                        if (!Strs.isBlank(it.getMmdb())){
                            loadMMDB(it.getMmdb());
                        }

                        DnsOption po = it.getDns();
                        ddns.setOption(it.getDdns());

                        if (dnsFuture.succeeded())
                            dnsServer.setOption(po);

                        if (proxyFuture.succeeded())
                            proxyServer.setProxyOption(it.getProxy());
                    });

                    Mmdb.initialize(vertx,12, TimeUnit.HOURS);

                    if (!Strs.isBlank(option.getMmdb())){
                        loadMMDB(option.getMmdb());
                    }

//                    LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
                    return Future.all(dnsFuture,proxyFuture)
                            .onSuccess(cf->{
                                if (!Strs.isBlank(option.getLogLevel())){
                                    setLoggerLevel(Level.toLevel(option.getLogLevel()));
                                }
                                long startCost = System.nanoTime()-start;
                                Ddnsp.logStartTime(Duration.ofNanos(startCost));
                            })
                            .map((Void)null);
                })
                .onFailure(t->{
                    t.printStackTrace();
                    System.exit(1);
                });

    }

    private static void setLoggerLevel(Level level){
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(level);
    }

    private static void loadMMDB(String mmdb){
        try {
            Mmdb.instance().load(mmdb);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
