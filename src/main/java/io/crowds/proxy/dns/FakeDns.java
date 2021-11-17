package io.crowds.proxy.dns;

import io.crowds.dns.DnsContext;
import io.crowds.proxy.routing.Router;
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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class FakeDns implements Handler<DnsContext> {
    private final static Logger logger= LoggerFactory.getLogger(FakeDns.class);

    private EventLoop executors;

    private String destStrategy;

    private IpPool ipv4Pool;

    private IpPool ipv6Pool;

    private Router router;

    private Map<Domain,FakeContext> domainFakeMap;
    private Map<InetAddress,FakeContext> addrFakeMap;

    public FakeDns(EventLoop executors,Router router, IPCIDR ipv4Cidr, IPCIDR ipv6Cidr,String destStrategy) {
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

    private boolean tryHitCache(DnsContext ctx, Domain domain){
        FakeContext context = domainFakeMap.get(domain);
        if (context==null)
            return false;
        if (!context.isAvailable()){
            executors.execute(()->domainFakeMap.remove(domain));
            return false;
        }
//        logger.warn("fakedns hit cache domain: {} fakeAddr:{} realAddr: {}",domain,context.getFakeAddr(),context.getRealAddr());
        InetAddress fakeAddr = context.getFakeAddr();
        ctx.resp(DnsOpCode.QUERY,DnsResponseCode.NOERROR,
                Collections.singletonList(new DefaultDnsRawRecord(domain.getName(), ctx.getQuestion().type(), context.remainTimeMillis()/1000, Unpooled.wrappedBuffer(fakeAddr.getAddress()))));

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

    private void mappingAndResp(DnsContext ctx, Domain domain, RealAddr realAddr, String tag){
        if (executors.inEventLoop()){
            var ipPool=DnsRecordType.AAAA.equals(domain.getType()) ? this.ipv6Pool:ipv4Pool;
            InetAddress fakeAddr = ipPool.getAvailableAddress();
            if (fakeAddr==null){
                logger.warn("unable get available addr from ipPool");
                return;
            }
            FakeContext fakeContext = new FakeContext(fakeAddr, domain.getName(), realAddr, tag, this.destStrategy);
            domainFakeMap.put(domain,fakeContext);
            addrFakeMap.put(fakeAddr,fakeContext);
            executors.schedule(()->{
                boolean del = false;
                FakeContext context = domainFakeMap.get(domain);
                if (context!=null&&Objects.equals(context.getId(),fakeContext.getId())){
                    domainFakeMap.remove(domain);
                    del=true;
                }
                context=addrFakeMap.get(fakeAddr);
                if (context!=null&&Objects.equals(context.getId(),fakeContext.getId())){
                    addrFakeMap.remove(fakeAddr);
                    del=true;
                }
                if (del)
                    ipPool.release(fakeAddr);
            }, (long) (realAddr.ttl()*1.5), TimeUnit.SECONDS);

//            logger.warn("domain:{} fakeAddr:{}  realAddr: {}",domain,fakeAddr,realAddr);

            ctx.resp(DnsOpCode.QUERY,DnsResponseCode.NOERROR,
                    Collections.singletonList(new DefaultDnsRawRecord(domain.getName(), ctx.getQuestion().type(), realAddr.ttl(), Unpooled.wrappedBuffer(fakeAddr.getAddress()))));
        }else{
            executors.execute(()->mappingAndResp(ctx, domain, realAddr, tag));
        }


    }

    @Override
    public void handle(DnsContext ctx) {
        DnsQuestion question = ctx.getQuestion();
        String name = question.name();
        if (name.endsWith(".")){
            name=name.substring(0,name.length()-1);
        }
        if((ipv4Pool!=null&&question.type()== DnsRecordType.A)||(ipv6Pool!=null&&question.type()==DnsRecordType.AAAA)){
            Domain domain = new Domain(name, question.type());
            if (tryHitCache(ctx,domain)) {
                return;
            }
            String tag = router.routing(name);
//            if (tag!=null){
//                logger.warn("domain: {} forward to tag: {}",domain,tag);
//            }
            ctx.recursionQuery(resp->{
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
                                String routingTag = router.routing(address.addr(), true);
                                if (routingTag != null) {
                                    //mapping
                                    mappingAndResp(ctx, domain, address, routingTag);
                                    return;
                                }
                            }else{
                                mappingAndResp(ctx,domain,address,tag);
                                return;
                            }
                        }
                    }
                    ctx.resp(resp);
                } catch (Exception e) {
                    if (!(e instanceof UnknownHostException)){
                        e.printStackTrace();
                    }
                    ctx.resp(resp);
                }
            });

        }else{

            ctx.recursionQuery(ctx::resp);
        }

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

    record Domain1(String name,DnsRecordType type){

    }

    class Domain{
        private String name;
        private DnsRecordType type;

        public Domain(String name, DnsRecordType type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public DnsRecordType getType() {
            return type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Domain domain = (Domain) o;
            return Objects.equals(name, domain.name) && Objects.equals(type, domain.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type);
        }

        @Override
        public String toString() {
            return "{" + "name='" + name + '\'' + ", type=" + type + '}';
        }
    }


}
