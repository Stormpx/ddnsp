package io.crowds;

import io.crowds.ddns.DDnsOption;
import io.crowds.dns.DnsOption;
import io.crowds.dns.RR;
import io.crowds.dns.RecordData;
import io.crowds.proxy.ProxyOption;
import io.crowds.proxy.dns.FakeOption;
import io.crowds.proxy.services.socks.SocksOption;
import io.crowds.proxy.services.transparent.TransparentOption;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.shadowsocks.Cipher;
import io.crowds.proxy.transport.shadowsocks.ShadowsocksOption;
import io.crowds.proxy.transport.vmess.Security;
import io.crowds.proxy.transport.vmess.User;
import io.crowds.proxy.transport.vmess.VmessOption;
import io.crowds.util.Ints;
import io.crowds.util.Strs;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DefaultDnsPtrRecord;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
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

    private final static Logger logger= LoggerFactory.getLogger(DDnspOptionLoader.class);

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

                optionChangeHandler.handle(new DDnspOption()
                        .setLogLevel(configuration.getString("logLevel","info"))
                        .setDns(toOption(configuration))
                        .setDdns(toDDnsOption(configuration))
                        .setProxy(toProxyOption(configuration)));
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
            DDnspOption dDnspOption = new DDnspOption()
                    .setLogLevel("info");
            DnsOption dnsOption =new DnsOption()
                    .setHost("127.0.0.1")
                    .setPort(53)
                    .setTtl(300)
                    .setDnsServers(provider.nameServerAddresses());

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
                    .map(json-> new DDnspOption()
                            .setLogLevel(json.getString("logLevel","info"))
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
            TransparentOption transparentOption = new TransparentOption();
            transparentOption.setEnable(true);
            transparentOption.setHost(transparentJson.getString("host","127.0.0.1"))
                    .setPort(transparentJson.getInteger("port",13452));
            proxy.setTransparent(transparentOption);
        }
        JsonArray proxiesArray = json.getJsonArray("proxies");
        if (proxiesArray!=null){
            List<ProtocolOption> protocolOptions=new ArrayList<>();
            for (int i = 0; i < proxiesArray.size(); i++) {
                try {
                    var protocolJson = proxiesArray.getJsonObject(i);
                    var protocol = protocolJson.getString("protocol");
                    var name = protocolJson.getString("name");
                    var connIdle = protocolJson.getInteger("connIdle");
                    ProtocolOption protocolOption = null;
                    if ("vmess".equalsIgnoreCase(protocol)){
                        protocolOption=parseVmess(protocolJson);
                    }else if ("ss".equalsIgnoreCase(protocol)){
                        protocolOption=parseSs(protocolJson);
                    }
                    if (protocolOption!=null){
                        protocolOption.setProtocol(protocol)
                                .setName(name);
                        if (connIdle!=null){
                            protocolOption.setConnIdle(connIdle<0?0:connIdle);
                        }
                        protocolOptions.add(protocolOption);
                    }

                } catch (Exception e) {
                    logger.warn("unable parse proxy option. because: {}",e.getMessage());
                }
            }
            proxy.setProxies(protocolOptions);
        }
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

        return proxy;
    }

    private VmessOption parseVmess(JsonObject json){
        VmessOption vmessOption = new VmessOption();
        var host=json.getString("host");
        var port=json.getInteger("port");
        vmessOption.setAddress(new InetSocketAddress(host, port));
        String uuid = json.getString("uid");
        Integer alterId = json.getInteger("alterId",0);
        if (Strs.isBlank(uuid)) {
            throw new NullPointerException("uid is required.");
        }
        vmessOption.setUser(new User(UUID.fromString(uuid),alterId));

        String securityStr = json.getString("security");
        Security security = Security.of(securityStr);
        vmessOption.setSecurity(security);

        var tls=json.getBoolean("tls",false);
        var tlsAllowInsecure=json.getBoolean("tlsAllowInsecure",false);
        var tlsServerName=json.getString("tlsServerName");
        vmessOption.setTls(tls)
                .setTlsAllowInsecure(tlsAllowInsecure)
                .setTlsServerName(tlsServerName);

        String network = json.getString("network");
        if ("ws".equalsIgnoreCase(network)){
            VmessOption.WsOption wsOption = new VmessOption.WsOption();
            JsonObject wsJson = json.getJsonObject("ws");
            String path = wsJson.getString("path","/");
            wsOption.setPath(path);
            JsonObject headersJson = wsJson.getJsonObject("headers");
            if (headersJson!=null){
                HttpHeaders headers = new DefaultHttpHeaders();
                for (Map.Entry<String, Object> entry : headersJson) {
                    headers.add(entry.getKey(),entry.getValue());
                } wsOption.setHeaders(headers);
            }
            vmessOption.setWs(wsOption);
            vmessOption.setNetWork("ws");
        }

        return vmessOption;
    }

    private ShadowsocksOption parseSs(JsonObject json){

        ShadowsocksOption shadowsocksOption = new ShadowsocksOption();
        var host=json.getString("host");
        var port=json.getInteger("port");
        InetSocketAddress address = new InetSocketAddress(host, port);
        String cipherStr = json.getString("cipher");
        Cipher cipher = Cipher.of(cipherStr);
        if (cipher==null){
            throw new IllegalArgumentException("invalid cipher: "+cipherStr);
        }
        String password = json.getString("password");
        if (Strs.isBlank(password)){
            throw new IllegalArgumentException("password is required");
        }
        shadowsocksOption.setAddress(address)
                .setCipher(cipher)
                .setPassword(password);
        return shadowsocksOption;
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
