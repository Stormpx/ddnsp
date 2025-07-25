package io.crowds.proxy.dns;

import io.crowds.dns.server.DnsContext0;
import io.crowds.proxy.routing.Router;
import io.crowds.util.AddrType;
import io.crowds.util.IPCIDR;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.dns.*;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class FakeDns implements Handler<DnsContext0> {
    private final static Logger logger= LoggerFactory.getLogger(FakeDns.class);

    private EventLoop executors;

    private String destStrategy;

    private IpPool ipv4Pool;

    private IpPool ipv6Pool;

    private Router router;

    private Map<Domain,FakeContext> domainFakeMap;
    private Map<InetAddress,FakeContext> addrFakeMap;

    public FakeDns(EventLoop executors, Router router, IPCIDR ipv4Cidr, IPCIDR ipv6Cidr, String destStrategy) {
        Objects.requireNonNull(executors);
        Objects.requireNonNull(router);
        this.executors = executors;
        this.router = router;
        this.destStrategy = destStrategy;
        if (ipv4Cidr!=null)
            this.ipv4Pool =new IpPool(ipv4Cidr);
        if (ipv6Cidr!=null)
            this.ipv6Pool=new IpPool(ipv6Cidr);
        if (this.ipv4Pool==null&&this.ipv6Pool==null)
            throw new IllegalArgumentException();
        this.domainFakeMap=new ConcurrentHashMap<>();
        this.addrFakeMap=new ConcurrentHashMap<>();
    }

    public FakeDns setRouter(Router router) {
        if (router!=null)
            this.router = router;
        return this;
    }

    private boolean tryHitCache(DnsContext0 ctx,DnsQuestion question, Domain domain){
        FakeContext context = domainFakeMap.get(domain);
        if (context==null)
            return false;
        if (!context.isAvailable()){
            if (executors.inEventLoop()){
                domainFakeMap.remove(domain);
            }else {
                executors.execute(() -> domainFakeMap.remove(domain,context));
            }
            return false;
        }
        InetAddress fakeAddr = context.getFakeAddr();
        ctx.resp(DnsOpCode.QUERY,DnsResponseCode.NOERROR,
                Collections.singletonList(new DefaultDnsRawRecord(domain.name(), question.type(), 60, Unpooled.wrappedBuffer(fakeAddr.getAddress()))));

        return true;
    }

    private RealAddr getAddress(DnsRecord record,DnsRecordType expectType) throws UnknownHostException {
        if (record.type()!=expectType){
            return null;
        }
        if (!(record instanceof DnsRawRecord)){
            return null;
        }
        DnsRawRecord rawRecord= (DnsRawRecord) record;
        return new RealAddr(rawRecord.timeToLive(),InetAddress.getByAddress(ByteBufUtil.getBytes(rawRecord.content())));
    }

    private void mappingAndResp(DnsContext0 ctx,DnsQuestion question, Domain domain, RealAddr realAddr, String tag){
        if (executors.inEventLoop()){
            var ipPool=DnsRecordType.AAAA.equals(domain.type()) ? this.ipv6Pool:ipv4Pool;
            InetAddress fakeAddr = ipPool.getAvailableAddress();
            if (fakeAddr==null){
                logger.warn("unable get available addr from ipPool");
                return;
            }
            FakeContext fakeContext = new FakeContext(fakeAddr, domain.name(), realAddr, tag, this.destStrategy);
            domainFakeMap.put(domain,fakeContext);
            addrFakeMap.put(fakeAddr,fakeContext);
            executors.schedule(()->{
                boolean del = domainFakeMap.remove(domain,fakeContext) | addrFakeMap.remove(fakeAddr,fakeContext);
                if (del)
                    ipPool.release(fakeAddr);
            }, (long) (realAddr.ttl()*1.5), TimeUnit.SECONDS);
            ctx.resp(DnsOpCode.QUERY,DnsResponseCode.NOERROR,
                    Collections.singletonList(new DefaultDnsRawRecord(domain.name(), question.type(), 60, Unpooled.wrappedBuffer(fakeAddr.getAddress()))));
        }else{
            executors.execute(()->mappingAndResp(ctx, question,domain, realAddr, tag));
        }


    }

    @Override
    public void handle(DnsContext0 ctx) {
        List<DnsQuestion> questions = ctx.getQuestions();
        if (questions.size()!=1){
            ctx.doQuery(ctx::resp);
            return;
        }
        DnsQuestion question = questions.getFirst();
        String name = question.name();
        if (name.endsWith(".")){
            name=name.substring(0,name.length()-1);
        }
        if((ipv4Pool!=null&&question.type()== DnsRecordType.A)||(ipv6Pool!=null&&question.type()==DnsRecordType.AAAA)){
            Domain domain = new Domain(name, question.type());
            if (tryHitCache(ctx,question,domain)) {
                return;
            }

            String tag = router.routing(ctx.getSender(),name);
            if (tag!=null&&!Objects.equals("ip",destStrategy)){
                mappingAndResp(ctx,question,domain,new RealAddr(1200,null),tag);
                return;
            }
            ctx.doQuery(resp->{
                try {
                    if (resp.code()!= DnsResponseCode.NOERROR){
                        ctx.resp(resp);
                        return;
                    }
                    int count = resp.count(DnsSection.ANSWER);
                    for (int i = 0; i < count; i++) {
                        DnsRecord record = resp.recordAt(DnsSection.ANSWER, i);
                        RealAddr address = getAddress(record, question.type());
                        if (address!=null){
                            if (tag==null) {
                                String routingTag = router.routingIp(address.addr(), true);
                                if (routingTag != null) {
                                    //mapping
                                    mappingAndResp(ctx,question, domain, address, routingTag);
                                    return;
                                }
                            }else{
                                mappingAndResp(ctx,question,domain,address,tag);
                                return;
                            }
                        }
                    }
                    ctx.resp(resp);
                } catch (Exception e) {
                    if (!(e instanceof UnknownHostException)){
                        logger.error("",e);
                    }
                    ctx.resp(resp);
                }
            });

        }else{
            ctx.doQuery(ctx::resp);
        }

    }

    public IPCIDR getIPv4cidr(){
        return ipv4Pool.getIpcidr();
    }
    public IPCIDR getIPv6cidr(){
        return ipv6Pool.getIpcidr();
    }

    public boolean isFakeIp(InetAddress address){
        if (this.ipv4Pool!=null&&address instanceof Inet4Address)
            return this.ipv4Pool.isMatch(address);
        else if (this.ipv6Pool!=null&&address instanceof Inet6Address){
            return this.ipv6Pool.isMatch(address);
        }else
            return false;
    }

    public FakeContext getFake(InetAddress address){
        FakeContext context = addrFakeMap.get(address);
        return context;
    }

    public FakeContext getFake(String domainName, AddrType addrType){
        var domain = new Domain(domainName,addrType==AddrType.IPV4?DnsRecordType.A:DnsRecordType.AAAA);
        FakeContext context = domainFakeMap.get(domain);
        return context;
    }
    record Domain(String name,DnsRecordType type){}



}
