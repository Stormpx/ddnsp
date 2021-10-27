package io.crowds;

import io.crowds.ddns.DDnsOption;
import io.crowds.dns.DnsOption;
import io.crowds.dns.RR;
import io.crowds.dns.RecordData;
import io.crowds.proxy.ProxyOption;
import io.crowds.proxy.services.socks.SocksOption;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DefaultDnsPtrRecord;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.impl.resolver.DnsResolverProvider;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.*;

public class DDnspOptionLoader {

    private Logger logger= LoggerFactory.getLogger(DDnspOptionLoader.class);

    private Vertx vertx;

    private ConfigRetriever configRetriever;

    private Handler<DDnspOption> optionChangeHandler;

    public DDnspOptionLoader(Vertx vertx) {
        this.vertx = vertx;
    }

    public void setFilePath(String configFile){
        if (configRetriever!=null){
            configRetriever.close();
        }
        logger.info("config path:{}",configFile);
        var configOption=new ConfigRetrieverOptions()
                .addStore(new ConfigStoreOptions()
                        .setType("file")
                        .setOptional(false)
                        .setFormat("yaml")
                        .setConfig(new JsonObject().put("path",configFile)));

        this.configRetriever= ConfigRetriever.create(vertx,configOption);

    }

    public DDnspOptionLoader optionChangeHandler(Handler<DDnspOption> optionChangeHandler) {
        this.optionChangeHandler = optionChangeHandler;
        return this;
    }

    public void reload(){
        if (configRetriever==null)
            return;

        configRetriever.listen(cc->{
            logger.info("new configuration arrive");
            Handler<DDnspOption> optionChangeHandler = this.optionChangeHandler;
            if (optionChangeHandler!=null){
                JsonObject configuration = cc.getNewConfiguration();
                optionChangeHandler.handle(new DDnspOption().setDns(toOption(configuration)).setDdns(toDDnsOption(configuration)).setProxy(toProxyOption(configuration)));
            }
        });

    }

    public Future<DDnspOption> load(){

        if (configRetriever==null){
            DnsResolverProvider provider = new DnsResolverProvider((VertxImpl) vertx,  new AddressResolverOptions());
            if (provider.nameServerAddresses().isEmpty()){
                throw new IllegalStateException("can not found default nameServers");
            }
            logger.info("find default nameServers: {}",provider.nameServerAddresses());
            DDnspOption DDnspOption = new DDnspOption();
            DnsOption dnsOption =new DnsOption()
                    .setHost("127.0.0.1")
                    .setPort(53)
                    .setTtl(300)
                    .setDnsServers(provider.nameServerAddresses());

            DDnspOption.setDns(dnsOption);
            DDnspOption.setDdns(new DDnsOption().setEnable(false));

            ProxyOption proxyOption = new ProxyOption();
            DDnspOption.setProxy(proxyOption);

            return Future.succeededFuture(DDnspOption);
        }else{

            return configRetriever.getConfig()
                    .onSuccess(it->{
                        reload();
                    })
                    .map(json-> new DDnspOption()
                            .setDns(toOption(json))
                            .setDdns(toDDnsOption(json))
                            .setProxy(toProxyOption(json))
                    );

        }
    }

    @SuppressWarnings(value = "unchecked")
    private DnsOption toOption(JsonObject config){
        JsonObject json=config.getJsonObject("dns",new JsonObject());
        DnsOption dnsOption = new DnsOption();
        dnsOption.setTtl(Optional.ofNullable(json.getInteger("ttl")).orElse(120))
                .setHost(Optional.ofNullable(json.getString("host")).orElse("0.0.0.0"))
                .setPort(Optional.ofNullable(json.getInteger("port")).filter(p->p>0&&p<=65535).orElse(53))
                .setDnsServers(convert((List<String>) json.getJsonArray("dnsServers").getList()))
//                .setRecordsMap(getHosts(json.getJsonArray("records").getList()))
                .setRrMap(getStaticRecord(json.getJsonArray("records").getList()))
        ;
        return dnsOption;
    }
    private DDnsOption toDDnsOption(JsonObject config){
        JsonObject json = config.getJsonObject("ddns", new JsonObject());
        return new DDnsOption()
                .setEnable(json.getBoolean("enable"))
                .setResolver(json.getString("resolver"))
                .setRefreshInterval(json.getInteger("refresh_interval"))
                .setDomain(json.getString("name"))
                .setTtl(json.getInteger("ttl",300))
                .setCf(json.getJsonObject("cf"))
                .setAli(json.getJsonObject("ali"));

    }

    private ProxyOption toProxyOption(JsonObject config){
        JsonObject json = config.getJsonObject("proxy", new JsonObject());
        var proxy=new ProxyOption();
        JsonObject socksJson = json.getJsonObject("socks");
        if (socksJson!=null){
            SocksOption socksOption = new SocksOption();
            socksOption.setEnable(socksJson.getBoolean("enable",false))
                    .setHost(socksJson.getString("host","127.0.0.1"))
                    .setPort(socksJson.getInteger("port",13450))
                    .setUsername(socksJson.getString("username"))
                    .setPassword(socksJson.getString("password"))
            ;
            proxy.setSocks(socksOption);
        }
        JsonObject transparentJson = json.getJsonObject("transparent");
        if (transparentJson!=null){

        }
        JsonArray proxiesArray = json.getJsonArray("proxies");
        if (proxiesArray!=null){

        }
        //                .setHost(Optional.ofNullable(json.getString("host")).orElse("127.0.0.1"))
//                .setPort(Optional.ofNullable(json.getInteger("port")).filter(p->p>0&&p<=65535).orElse(23540));

        return proxy;
    }

    private List<InetSocketAddress> convert(List<String> serverList){

        List<InetSocketAddress> addressList=new ArrayList<>();

        for (String s : serverList) {
            if (s==null||s.isBlank())
                continue;

            String[] strings = s.split(":");
            String host="";
            int port=53;
            host=strings[0];
            if (strings.length>1){
                port=Integer.parseInt(strings[1]);
            }
            InetSocketAddress address = new InetSocketAddress(host, port);
            addressList.add(address);
        }

        return addressList;
    }


    public Map<String, RR> getHosts(List<String> list) {
        try {
            Map<String,RR> map=new HashMap<>();
            for (String str : list) {
                if (str==null)
                    continue;
                String[] strings = str.trim().split(" ");
                if (strings.length<2){
                    continue;
                }
                String domain=strings[0];
                if (!domain.endsWith(".")){
                    domain=domain+".";
                }
                RR rr = new RR(strings[1]);
                map.put(domain,rr);
            }
            return map;
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, RecordData> getStaticRecord(List<String> rr){
        Map<String,RecordData> map=new HashMap<>();
        for (String str : rr) {
            try {
                if (str==null)
                    continue;
                String[] strings = str.trim().split(" ");
                if (strings.length<3){
                    continue;
                }
                String domain=strings[0];
                if (!domain.endsWith(".")){
                    domain=domain+".";
                }
                String type = strings[1];
                DnsRecordType dnsRecordType = DnsRecordType.valueOf(type);
                if (dnsRecordType==null)
                    continue;
                String raw = strings[2];
                DnsRecord record=null;
                if (dnsRecordType==DnsRecordType.A){
                    record =new DefaultDnsRawRecord(domain,DnsRecordType.A,0, Unpooled.wrappedBuffer(Inet4Address.getByName(raw).getAddress()));
                }else if (dnsRecordType==DnsRecordType.AAAA){
                    record =new DefaultDnsRawRecord(domain,DnsRecordType.A,0, Unpooled.wrappedBuffer(Inet6Address.getByName(raw).getAddress()));
                }else if (dnsRecordType==DnsRecordType.CNAME){
                    new DefaultDnsRawRecord(domain,DnsRecordType.CNAME,0,Unpooled.wrappedBuffer(raw.getBytes()));
                }else if (dnsRecordType==DnsRecordType.PTR) {
                    record=new DefaultDnsPtrRecord(domain, DnsRecord.CLASS_IN,0,raw);
                }
                if (record!=null){
                    map.computeIfAbsent(domain,k->new RecordData()).add(record);
                }
            } catch (Exception e) {
                logger.warn("parse Resource record {} failed cause:{}",str,e.getMessage());
                continue;
            }
        }
        return map;
    }



}
