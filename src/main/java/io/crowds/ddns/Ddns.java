package io.crowds.ddns;

import io.crowds.ddns.resolve.CloudFlareDnsResolver;
import io.crowds.ddns.resolve.DnsResolver;
import io.crowds.ddns.resolve.DomainRecord;
import io.crowds.util.Inet;
import io.crowds.util.Strs;
import io.netty.util.NetUtil;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.TimeoutStream;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Ddns {
    private final static Logger logger= LoggerFactory.getLogger(Ddns.class);
    private Vertx vertx;
    private HttpClient httpClient;

    private DDnsOption option;

    private Map<String, IpProvider> ipProviders;
    private Map<String,DnsResolver> resolvers;
    private List<Context> contexts;

    public Ddns(Vertx vertx, DDnsOption option) {
        this.vertx = vertx;
        this.httpClient=vertx.createHttpClient(new HttpClientOptions()
                        .setShared(true)
                .setTryUseCompression(true).setKeepAlive(true));
        setOption(option);
        logger.info("ddns created");
    }


    private void cancelContext(){
        if (this.contexts==null)
            return;
        for (Context context : this.contexts) {
            context.cancel();
        }
        this.contexts=null;
    }

    private void createIpHelper(JsonArray array){
        this.ipProviders =new ConcurrentHashMap<>();
        this.ipProviders.put("http",new HttpIpProvider(httpClient));
        if (array==null)
            return;
        for (int i = 0; i < array.size(); i++) {
            JsonObject json = array.getJsonObject(i);
            String name = json.getString("name");
            Objects.requireNonNull(name,"ipProvider name is required");
            String src = json.getString("src");
            IpProvider ipProvider;
            if ("http".equalsIgnoreCase(src)){
                var urls=Optional.ofNullable(json.getJsonArray("urls"))
                        .orElseGet(JsonArray::new)
                        .stream()
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .filter(str->str.startsWith("http"))
                        .collect(Collectors.toList());
                ipProvider =new HttpIpProvider(httpClient,urls);
            }else if ("nic".equalsIgnoreCase(src)){
                ipProvider =new InterfaceIpProvider(json);
            } else if ("static".equalsIgnoreCase(src)){
                String ipv4 = json.getString("ipv4",json.getString("ip"));
                String ipv6 = json.getString("ipv6");
                if (ipv4==null||ipv6==null){
                    logger.warn("iphelper {} both ipv4 and ipv6 is set to null. so skipped.",name);
                    continue;
                }
                ipProvider=new StaticIpProvider(ipv4,ipv6);
            }else{
                throw new IllegalArgumentException("src "+src+" not supported");
            }
            this.ipProviders.put(name, ipProvider);
        }
    }

    private void createResolver(JsonArray array){
        this.resolvers=new ConcurrentHashMap<>();
        if (array==null)
            return;
        for (int i = 0; i < array.size(); i++) {
            JsonObject json = array.getJsonObject(i);
            String name = json.getString("name");
            Objects.requireNonNull(name,"resolver name is required");
            String type = json.getString("type");
            DnsResolver resolver;
            if ("cf".equalsIgnoreCase(type)){
                resolver=new CloudFlareDnsResolver(httpClient,json);
            }else{
                throw new IllegalArgumentException("type "+type+" not supported");
            }
            this.resolvers.put(name,resolver);
        }
    }

    private void createContext(JsonArray domains){
        this.contexts=new ArrayList<>();
        if (domains==null)
            return;
        for (int i = 0; i < domains.size(); i++) {
            JsonObject json = domains.getJsonObject(i);
            var domain = json.getString("name");
            var ttl = json.getInteger("ttl");
            var resolver = json.getString("resolver");
            var provider = json.getString("provider");
            var refreshInterval = json.getInteger("interval");
            var mode = json.getString("model", "ip");
            Objects.requireNonNull(domain,"domain name is required");
            Objects.requireNonNull(resolver,"domain resolver is required");
            Objects.requireNonNull(mode,"mode is required");
            if (domain.length()>255){
                throw new IllegalArgumentException("domain length > 255");
            }
            if (ttl==null)
                ttl=300;
            if (Strs.isBlank(provider))
                provider="http";
            if (refreshInterval==null)
                refreshInterval= 86400;

            boolean ipv4= mode.equals("ipv4")||mode.equals("ip");
            boolean ipv6= mode.equals("ipv6")||mode.equals("ip");

            Context context = new Context(domain, ttl, refreshInterval, provider,
                    resolver,ipv4,ipv6);
            context.start();
            this.contexts.add(context);

        }
    }


    public void setOption(DDnsOption option){
        if (option==null){
            throw new RuntimeException("option == null");
        }
//        if (this.option!=null&&this.option.isEnable()==option.isEnable()){
//            return;
//        }
        if (Objects.equals(option,this.option)){
            return;
        }

        if (!option.isEnable()) {
            cancelContext();
            return;
        }

        JsonArray ipHelpers = option.getIpProviders();
        if (this.option==null||!Objects.equals(ipHelpers,this.option.getIpProviders())) {
            createIpHelper(ipHelpers);
        }
        JsonArray resolvers = option.getResolvers();
        if (this.option==null||!Objects.equals(resolvers,this.option.getResolvers())) {
            createResolver(resolvers);
        }
        JsonArray domains = option.getDomains();
        if (this.option==null||!Objects.equals(domains,this.option.getDomains())) {
            cancelContext();
            createContext(domains);
        }
        this.option=option;


//        String resolver = Optional.ofNullable(option.getResolver()).orElse("");
//        if (resolver.contains("cf")){
//            if (this.dnsResolver!=null&&this.dnsResolver instanceof CloudFlareDnsResolver){
//                this.dnsResolver.setConfig(option.getCf());
//                return;
//            }
//            this.dnsResolver=new CloudFlareDnsResolver(httpClient,option.getCf());
//
//        }else if (resolver.contains("ali")){
//
//        }
//        if (this.dnsResolver==null){
//            throw new RuntimeException("dnsResolver "+resolver+" not find ");
//        }
    }

//    public void startTimer(){
//
//        int refreshInterval = option.getRefreshInterval();
//        if (refreshInterval<=0)
//            refreshInterval=3600;
//
//        vertx.setTimer(refreshInterval* 1000L, id->{
//
//            refreshResolve(true);
//        });
//
//    }
//
//    public void refreshResolve(boolean loop){
//
//        if (loop) {
//            startTimer();
//        }
//        if (!option.isEnable())
//            return;
//        logger.info("try start refresh resolve");
//        httpIpProvider.getCurIpv4()
//                .onFailure(t->logger.error("",t))
//                .onSuccess(content->{
//                    logger.info("get cur ip:{}",content);
//                    if (option.getDomain()==null||option.getDomain().isBlank()){
//                        logger.warn("domain is null can not further operation");
//                        return;
//                    }
//                    dnsResolver.getRecord(option.getDomain())
//                           .onFailure(t->logger.error("",t))
//                           .onSuccess(list->{
//                               logger.info("dns record size:{}",list.size());
//                               var r=list.stream()
//                                       .filter(it->it.getName().equals(option.getDomain()))
//                                       //only check type A
//                                       .filter(it->it.getType().equals("A"))
//                                       .findFirst()
//                                       .orElse(null);
//                               if (r==null) {
//                                   logger.warn("can not find dns record name:{}",option.getDomain());
//                                   return;
//                               }
//                               if (!content.equals(r.getContent())){
//                                   logger.info("cur content:{} != dns record content:{} update to cur content",content,r.getContent());
//                                   int ttl = r.getTtl();
//                                   if (option.getTtl()!=null&&option.getTtl()>0)
//                                       ttl=option.getTtl();
//
//                                   dnsResolver.updateDnsResolve(r.setContent(content).setTtl(ttl))
//                                        .onFailure(t->logger.error("",t))
//                                        .onSuccess(v->logger.info("update suc"))
//                                   ;
//                               }
//                           });
//
//                });
//    }

    private class Context{
        private TimeoutStream timer;

        private String domain;
        private Integer ttl;
        private long refreshInterval;
        private String provider;
        private String resolver;

        private boolean ipv4;
        private boolean ipv6;

        public Context(String domain, Integer ttl, long refreshInterval, String provider, String resolver,boolean ipv4,boolean ipv6) {
            this.domain = domain;
            this.ttl = ttl;
            this.refreshInterval = refreshInterval;
            this.provider = provider;
            this.resolver = resolver;
            this.ipv4=ipv4;
            this.ipv6=ipv6;
        }

        private Future<Void> updateIpv4(IpProvider ipProvider,DnsResolver resolver,List<DomainRecord> records){
            return ipProvider.getIpv4()
                             .map(NetUtil::createInetAddressFromIpAddressString)
                             .compose(ip->{
                                var r=records.stream()
                                             .filter(it->it.getName().equals(domain))
                                             .filter(DomainRecord::isIpv4Record)
                                             .findFirst()
                                             .orElse(null);
                                if (r==null){
                                    r=new DomainRecord()
                                            .setName(domain)
                                            .setType("A");
                                    logger.info("context {} > A record not exists. create with ip:{}",domain,ip);
                                }else{
                                    InetAddress oldIp = r.getInetAddress();
                                    if (Objects.equals(ip,oldIp)){
                                        return Future.succeededFuture();
                                    }
                                    logger.info("context {} > ip has been changed old:{} new:{}",domain,r.getContent(),ip);
                                }
                                r.setTtl(ttl).setContent(ip.toString());
                                return resolver.updateDnsResolve(r);
                            });
        }

        private Future<Void> updateIpv6(IpProvider ipProvider,DnsResolver resolver,List<DomainRecord> records){
            return ipProvider.getIpv6()
                             .map(NetUtil::createInetAddressFromIpAddressString)
                             .compose(ip->{
                                 var r=records.stream()
                                              .filter(it->it.getName().equals(domain))
                                              .filter(DomainRecord::isIpv6Record)
                                              .findFirst()
                                              .orElse(null);
                                 if (r==null){
                                     r=new DomainRecord()
                                             .setName(domain)
                                             .setType("AAAA");
                                     logger.info("context {} > AAAA record not exists. create with ip:{}",domain,ip);
                                 }else{
                                     InetAddress oldIp = r.getInetAddress();
                                     if (Objects.equals(ip,oldIp)){
                                         return Future.succeededFuture();
                                     }
                                     logger.info("context {} > ip has been changed old:{} new:{}",domain,r.getContent(),ip);
                                 }
                                 r.setTtl(ttl).setContent(ip.toString());
                                 return resolver.updateDnsResolve(r);
                             });
        }

        private void resolve(){
            if (!ipv4&&!ipv6)
                return;
            logger.info("context {} > start refresh resolve",domain);
            IpProvider ipProvider = Ddns.this.ipProviders.get(provider);
            if (ipProvider ==null){
                logger.error("context {} > refresh resolve failed cause: ipHelper {} not found",domain,this.provider);
                return;
            }
            DnsResolver resolver = resolvers.get(this.resolver);
            if (resolver==null){
                logger.error("context {} > refresh resolve failed cause: resolver {} not found",domain,this.resolver);
                return;
            }
            resolver.getRecord(domain)
                    .onFailure(e->logger.error("context {} > get exists records failed because: {}",domain,e.getMessage()))
                    .onSuccess(records->{
                        if (ipv4){
                            updateIpv4(ipProvider,resolver,records)
                                    .onFailure(t->logger.error("context {} > refresh ipv4 resolve failed cause: {}",domain,t.getMessage()))
                                    .onSuccess(v->logger.info("context {} > refresh ipv4 resolve completed.",domain));
                        }
                        if (ipv6){
                            updateIpv6(ipProvider,resolver,records)
                                    .onFailure(t->logger.error("context {} > refresh ipv6 resolve failed cause: {}",domain,t.getMessage()))
                                    .onSuccess(v->logger.info("context {} > refresh ipv6 resolve completed.",domain));
                        }
                    });
        }

        public void start(){
            this.timer=vertx.periodicStream(refreshInterval*1000L)
                    .handler(id->{
                        resolve();
                    });

        }

        public void cancel(){
            if (this.timer!=null){
                this.timer.cancel();
            }
        }


    }

}
