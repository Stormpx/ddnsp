package io.crowds;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.crowds.ddns.Ddns;
import io.crowds.dns.DnsClient;
import io.crowds.dns.DnsServer;
import io.crowds.dns.DnsOption;
import io.crowds.proxy.ProxyOption;
import io.crowds.proxy.ProxyServer;
import io.crowds.util.Strs;
import io.netty.channel.epoll.Epoll;
import io.netty.util.ResourceLeakDetector;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.Option;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.spi.resolver.ResolverProvider;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Arrays;


public class Main {
    public static void main(String[] args) {
//        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
        System.getProperties().setProperty("vertx.disableDnsResolver","true");
        Vertx vertx = Vertx.vertx(new VertxOptions()
                .setPreferNativeTransport(true));
        CLI cli = CLI.create("ddnsp")
                .addOption(new Option().setShortName("c").setLongName("config").setMultiValued(false).setRequired(false));

        CommandLine line = cli.parse(Arrays.asList(args));
        String configFile=line.getOptionValue("c");

        DDnspOptionLoader loader = new DDnspOptionLoader(vertx);

        if (configFile != null) {
           loader.setFilePath(configFile);
        }

        loader.load()
                .compose(option->{

                    ProxyOption proxyOption = option.getProxy();
                    DnsOption dnsOption = option.getDns();
                    var dnsClient=new DnsClient(vertx.nettyEventLoopGroup(), dnsOption);
                    InetSocketAddress socketAddress = new InetSocketAddress(dnsOption.getHost(), dnsOption.getPort());
                    DnsServer dnsServer = new DnsServer(vertx.nettyEventLoopGroup(),dnsClient).setOption(dnsOption);
                    Ddns ddns = new Ddns(vertx, option.getDdns());
//                    ddns.startTimer();

                    Future<Void> dnsFuture = dnsServer.start(socketAddress);

                    ProxyServer proxyServer = new ProxyServer(vertx.nettyEventLoopGroup()).setProxyOption(proxyOption);
//                    new InetSocketAddress(proxyOption.getHost(), proxyOption.getPort())
                    Future<Void> proxyFuture = proxyServer.start()
                            .onSuccess(v->{
                                if (proxyServer.getFakeDnsHandler()!=null){
                                    dnsServer.contextHandler(proxyServer.getFakeDnsHandler());
                                }
                            });

                    loader.optionChangeHandler(it -> {
                        if (!Strs.isBlank(it.getLogLevel())){
                            setLoggerLevel(Level.toLevel(it.getLogLevel()));
                        }
                        DnsOption po = it.getDns();
                        ddns.setOption(it.getDdns());
                        dnsClient.setDnsOption(po);

                        if (dnsFuture.succeeded())
                            dnsServer.setOption(po);

                        if (proxyFuture.succeeded())
                            proxyServer.setProxyOption(it.getProxy());
                    });

//                    LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
                    return CompositeFuture.any(dnsFuture,proxyFuture)
                            .onSuccess(cf->{
                                if (!Strs.isBlank(option.getLogLevel())){
                                    setLoggerLevel(Level.toLevel(option.getLogLevel()));
                                }
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
}
