package io.crowds;

import io.crowds.ddns.Ddns;
import io.crowds.dns.DnsServer;
import io.crowds.dns.DnsOption;
import io.crowds.proxy.ProxyOption;
import io.crowds.proxy.ProxyServer;
import io.netty.channel.epoll.Epoll;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.Option;

import java.net.InetSocketAddress;
import java.util.Arrays;


public class Main {
    public static void main(String[] args) {
        System.out.println(Epoll.isAvailable());
        Vertx vertx = Vertx.vertx(new VertxOptions().setPreferNativeTransport(true));
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
                    var dnsClient=new io.crowds.dns.DnsClient(vertx.nettyEventLoopGroup(), dnsOption);
                    InetSocketAddress socketAddress = new InetSocketAddress(dnsOption.getHost(), dnsOption.getPort());
                    DnsServer dnsServer = new DnsServer(vertx.nettyEventLoopGroup(),dnsClient).setOption(dnsOption);
                    Ddns ddns = new Ddns(vertx, option.getDdns());
                    ddns.startTimer();

                    Future<Void> dnsFuture = dnsServer.start(socketAddress);

                    ProxyServer proxyServer = new ProxyServer(vertx.nettyEventLoopGroup()).setProxyOption(proxyOption);
                    Future<Void> proxyFuture = proxyServer.start(new InetSocketAddress(proxyOption.getHost(), proxyOption.getPort()));

                    loader.optionChangeHandler(it -> {
                        DnsOption po = it.getDns();
                        ddns.setOption(it.getDdns());
                        dnsClient.setDnsOption(po);

                        if (dnsFuture.succeeded())
                            dnsServer.setOption(po);

                        if (proxyFuture.succeeded())
                            proxyServer.setProxyOption(it.getProxy());
                    });

                    return CompositeFuture.any(dnsFuture,proxyFuture)
                            .map((Void)null);
                })
                .onFailure(t->{
                    t.printStackTrace();
                    System.exit(1);
                });

    }
}
