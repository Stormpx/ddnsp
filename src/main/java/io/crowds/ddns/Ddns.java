package io.crowds.ddns;

import io.crowds.ddns.resolve.CloudFlareDnsResolver;
import io.crowds.ddns.resolve.DnsResolver;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class Ddns {
    private final static Logger logger= LoggerFactory.getLogger(Ddns.class);
    private Vertx vertx;
    private HttpClient httpClient;
    private IpHelper ipHelper;
    private DnsResolver dnsResolver;

    private DDnsOption option;

    public Ddns(Vertx vertx, DDnsOption option) {
        this.vertx = vertx;
        this.httpClient=vertx.createHttpClient(new HttpClientOptions().setTryUseCompression(true).setKeepAlive(true));
        this.ipHelper=new IpInfoIpHelper(httpClient);
        setOption(option);
        logger.info("ddns created");
    }

    public void setOption(DDnsOption option){
        this.option=option;
        if (this.option==null){
            throw new RuntimeException("option == null");
        }
        if (!option.isEnable())
            return;

        String resolver = Optional.ofNullable(option.getResolver()).orElse("");
        if (resolver.contains("cf")){
            if (this.dnsResolver!=null&&this.dnsResolver instanceof CloudFlareDnsResolver){
                this.dnsResolver.setConfig(option.getCf());
                return;
            }
            this.dnsResolver=new CloudFlareDnsResolver(httpClient,option.getCf());

        }else if (resolver.contains("ali")){

        }
        if (this.dnsResolver==null){
            throw new RuntimeException("dnsResolver "+resolver+" not find ");
        }
    }

    public void startTimer(){

        int refreshInterval = option.getRefreshInterval();
        if (refreshInterval<=0)
            refreshInterval=3600;

        vertx.setTimer(refreshInterval*1000, id->{

            refreshResolve(true);
        });

    }

    public void refreshResolve(boolean loop){

        if (loop) {
            startTimer();
        }
        if (!option.isEnable())
            return;
        logger.info("try start refresh resolve");
        ipHelper.getCurIpv4()
                .onFailure(t->logger.error("",t))
                .onSuccess(content->{
                    logger.info("get cur ip:{}",content);
                    if (option.getDomain()==null||option.getDomain().isBlank()){
                        logger.warn("domain is null can not further operation");
                        return;
                    }
                    dnsResolver.getRecord(option.getDomain())
                           .onFailure(t->logger.error("",t))
                           .onSuccess(list->{
                               logger.info("dns record size:{}",list.size());
                               var r=list.stream()
                                       .filter(it->it.getName().equals(option.getDomain()))
                                       //only check type A
                                       .filter(it->it.getType().equals("A"))
                                       .findFirst()
                                       .orElse(null);
                               if (r==null) {
                                   logger.warn("can not find dns record name:{}",option.getDomain());
                                   return;
                               }
                               if (!content.equals(r.getContent())){
                                   logger.info("cur content:{} != dns record content:{} update to cur content",content,r.getContent());
                                   int ttl = r.getTtl();
                                   if (option.getTtl()!=null&&option.getTtl()>0)
                                       ttl=option.getTtl();

                                   dnsResolver.updateDnsResolve(r.getrId(),r.setContent(content).setTtl(ttl))
                                        .onFailure(t->logger.error("",t))
                                        .onSuccess(v->logger.info("update suc"))
                                   ;
                               }
                           });

                });


    }

}
