package io.crowds;

import io.crowds.ddns.DDnsOption;
import io.crowds.dns.DnsOption;
import io.crowds.dns.RecordData;
import io.crowds.proxy.ProxyOption;
import io.crowds.proxy.common.sniff.SniffOption;
import io.crowds.proxy.dns.FakeOption;
import io.crowds.proxy.services.http.HttpOption;
import io.crowds.proxy.services.socks.SocksOption;
import io.crowds.proxy.services.transparent.TransparentOption;
import io.crowds.proxy.services.tun.TunServerOption;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.proxy.ProtocolOptionFactory;
import io.crowds.util.Ints;
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
import io.vertx.core.dns.impl.DnsAddressResolverProvider;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class DdnspOptionLoader {

    private final static Logger logger= LoggerFactory.getLogger(DdnspOptionLoader.class);

    private final Vertx vertx;

    private ConfigRetriever configRetriever;

    private Handler<DdnspOption> optionChangeHandler;

    public DdnspOptionLoader(Vertx vertx) {
        this.vertx = vertx;
    }

    public void setFilePath(String configFile){
        if (configRetriever!=null){
            configRetriever.close();
        }
        logger.info("config path: {}",configFile);
        var configOption=new ConfigRetrieverOptions()
                .setScanPeriod(TimeUnit.SECONDS.toMillis(30))
                .addStore(new ConfigStoreOptions()
                        .setType("file")
                        .setOptional(false)
                        .setFormat("yaml")
                        .setConfig(new JsonObject().put("path",configFile)));

        this.configRetriever= ConfigRetriever.create(vertx,configOption);

    }

    public DdnspOptionLoader optionChangeHandler(Handler<DdnspOption> optionChangeHandler) {
        this.optionChangeHandler = optionChangeHandler;
        return this;
    }

    public void reload(){
        if (configRetriever==null)
            return;

        configRetriever.listen(cc->{
            logger.info("new configuration arrive");
            Handler<DdnspOption> optionChangeHandler = this.optionChangeHandler;
            if (optionChangeHandler!=null){
                JsonObject configuration = cc.getNewConfiguration();

                optionChangeHandler.handle(toDDnspOption(configuration));
            }
        });

    }

    public Future<DdnspOption> load(){

        if (configRetriever==null){
            DnsAddressResolverProvider provider = DnsAddressResolverProvider.create((io.vertx.core.internal.VertxInternal) vertx, new AddressResolverOptions());
            if (provider.nameServerAddresses().isEmpty()){
                throw new IllegalStateException("can not found default nameServers");
            }
            logger.info("find default nameServers: {}",provider.nameServerAddresses());
            DdnspOption dDnspOption = new DdnspOption()
                    .setLogLevel("info");
            DnsOption dnsOption =new DnsOption()
                    .setHost("127.0.0.1")
                    .setPort(53)
                    .setTtl(300)
                    .setDnsServers(provider.nameServerAddresses()
                            .stream()
                            .map(inet->{
                                int port = inet.getPort()==0?53:inet.getPort();
                                String host = inet.getAddress().getHostAddress();
                                if (inet.getAddress() instanceof Inet6Address){
                                    host="[" + host +"]";
                                }
                                return URI.create("dns://"+host+":"+port);
                            })
                            .collect(Collectors.toList())
                    );

            dDnspOption.setDns(dnsOption);
            dDnspOption.setDdns(new DDnsOption().setEnable(false));

            ProxyOption proxyOption = new ProxyOption();
            dDnspOption.setProxy(proxyOption);


            return Future.succeededFuture(dDnspOption);
        }else{

            return configRetriever.getConfig()
                    .onSuccess(it->{
                        reload();
                    })
                    .map(this::toDDnspOption);

        }
    }

    private DdnspOption toDDnspOption(JsonObject json){
        return new DdnspOption()
                .setLogLevel(json.getString("logLevel","info"))
                .setMmdb(json.getString("mmdb"))
                .setDns(toOption(json))
                .setDdns(toDDnsOption(json))
                .setProxy(toProxyOption(json));
    }

    @SuppressWarnings(value = "unchecked")
    private DnsOption toOption(JsonObject config){
        JsonObject json=config.getJsonObject("dns",new JsonObject());
        DnsOption dnsOption = new DnsOption();
        dnsOption.setEnable(json.getBoolean("enable"))
                .setTtl(Optional.ofNullable(json.getInteger("ttl")).orElse(120))
                .setHost(Optional.ofNullable(json.getString("host")).orElse("0.0.0.0"))
                .setPort(Optional.ofNullable(json.getInteger("port")).filter(p->p>0&&p<=65535).orElse(53))
                .setDnsServers(convert(json.getJsonArray("dnsServers")))
                .setIpv6(json.getBoolean("ipv6",false))
//                .setRecordsMap(getHosts(json.getJsonArray("records").getList()))
                .setRrMap(getStaticRecord(json.getJsonArray("records",JsonArray.of()).getList()))
        ;
        return dnsOption;
    }
    private DDnsOption toDDnsOption(JsonObject config){
        JsonObject json = config.getJsonObject("ddns", new JsonObject());
        return new DDnsOption()
                .setEnable(json.getBoolean("enable"))
                .setIpProviders(json.getJsonArray("ipProviders"))
                .setResolvers(json.getJsonArray("resolvers"))
                .setDomains(json.getJsonArray("domains"))
                ;

    }

    private ProxyOption toProxyOption(JsonObject config){
        JsonObject json = config.getJsonObject("proxy", new JsonObject());
        var proxy=new ProxyOption();
        JsonObject httpJson = json.getJsonObject("http");
        if (httpJson!=null){
            HttpOption httpOption = new HttpOption();
            httpOption.setEnable(httpJson.getBoolean("enable",false))
                      .setHost(httpJson.getString("host","127.0.0.1"))
                      .setPort(httpJson.getInteger("port",13448))
                      .setCert(Optional.ofNullable(httpJson.getString("cert")).map(Path::of).orElse(null))
                      .setKey(Optional.ofNullable(httpJson.getString("key")).map(Path::of).orElse(null))
                      .setKeyPassword(httpJson.getString("keyPass"));
            proxy.setHttp(httpOption);
        }
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
            TransparentOption transparentOption = new TransparentOption();
            transparentOption.setEnable(transparentJson.getBoolean("enable",true));
            transparentOption.setHost(transparentJson.getString("host","127.0.0.1"))
                    .setPort(transparentJson.getInteger("port",13452));
            proxy.setTransparent(transparentOption);
        }
        JsonObject tunJson = json.getJsonObject("tun");
        if (tunJson!=null){
            TunServerOption tunServerOption = new TunServerOption();
            tunServerOption.setEnable(tunJson.getBoolean("enable",false));
            tunServerOption.setName(tunJson.getString("name","tun0"))
                           .setMtu(tunJson.getInteger("mtu",1500))
                           .setIgnoreAddress(tunJson.getJsonArray("ignoreAddress"));
            proxy.setTun(tunServerOption);
        }

        JsonArray proxiesArray = json.getJsonArray("proxies");
        if (proxiesArray!=null){
            List<ProtocolOption> protocolOptions=new ArrayList<>();
            for (int i = 0; i < proxiesArray.size(); i++) {
                try {
                    var protocolJson = proxiesArray.getJsonObject(i);
                    ProtocolOption protocolOption = ProtocolOptionFactory.newOption(protocolJson);
                    if (protocolOption!=null){
                        protocolOptions.add(protocolOption);
                    }
                } catch (Exception e) {
                    logger.error(e.getMessage(),e.getCause());
                }
            }
            proxy.setProxies(protocolOptions);
        }

        JsonArray selectors = json.getJsonArray("selectors");
        proxy.setSelectors(selectors);

        JsonArray rulesJsonArr = json.getJsonArray("rules");
        if (rulesJsonArr!=null){
            List<String> rules=new ArrayList<>();
            for (int i = 0; i < rulesJsonArr.size(); i++) {
                rules.add(rulesJsonArr.getString(i));
            }
            proxy.setRules(rules);
        }

        JsonObject fakeDnsJson = json.getJsonObject("fakeDns");
        if (fakeDnsJson!=null){
            FakeOption fakeOption = new FakeOption();
            fakeOption.setIpv4Pool(fakeDnsJson.getString("ipv4Pool"));
            fakeOption.setIpv6Pool(fakeDnsJson.getString("ipv6Pool"));
            String destStrategy = fakeDnsJson.getString("destStrategy");
            if (destStrategy!=null){
                fakeOption.setDestStrategy(destStrategy);
            }
            proxy.setFakeDns(fakeOption);
        }

        JsonObject sniffJson = json.getJsonObject("sniff");
        if (sniffJson!=null){
            SniffOption sniffOption = new SniffOption();
            sniffOption.setPorts(Objects.requireNonNullElse(sniffJson.getJsonArray("ports"),JsonArray.of()).stream()
                                        .map(Objects::toString)
                                        .map(PortRange::parse)
                                        .filter(Objects::nonNull)
                                        .flatMap(PortRange::ports)
                                        .collect(Collectors.toSet()));
            sniffOption.setIgnoreDomain(Objects.requireNonNullElse(sniffJson.getJsonArray("ignoreDomain"), JsonArray.of()).stream()
                                               .filter(it->it instanceof String)
                                               .map(Object::toString)
                                               .collect(Collectors.toSet()));
            proxy.setSniff(sniffOption);
        }

        return proxy;
    }

    private List<URI> convert(JsonArray serverList){

        return serverList.stream()
                .filter(Objects::nonNull)
                .filter(o-> o instanceof String)
                .map(Object::toString)
                .filter(s->!s.isBlank())
                .map(s->{
                    if (!s.matches("\\w+://.*")){
                        s = "dns://"+s;
                    }
                    URI uri = URI.create(s);
                    return uri.getHost() == null ? null : uri;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

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
                    record =new DefaultDnsRawRecord(domain,DnsRecordType.AAAA,0, Unpooled.wrappedBuffer(Inet6Address.getByName(raw).getAddress()));
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
            }
        }
        return map;
    }

    record PortRange(int startInclusive, int endExclusive){

        static PortRange parse(String s){
            try {
                int port = Integer.parseInt(s);
                if (!Ints.isAvailablePort(port)){
                    return null;
                }
                return new PortRange(port,port+1);
            } catch (NumberFormatException e) {
                int index = s.indexOf("-",1);
                if (index==-1){
                    return null;
                }
                String[] split = s.split("-", 2);
                try {
                    int beginPort = Integer.parseInt(split[0]);
                    int toPort = Integer.parseInt(split[1]);
                    if (!Ints.isAvailablePort(beginPort)||!Ints.isAvailablePort(toPort)){
                        return null;
                    }
                    return new PortRange(beginPort,toPort);
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
        }

        Stream<Integer> ports(){
            return IntStream.range(startInclusive,endExclusive).boxed();
        }
    }


}
