package io.crowds.proxy.dns;

import io.crowds.proxy.DomainNetAddr;
import io.crowds.proxy.NetAddr;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class FakeContext {
    private long id;
    private InetAddress fakeAddr;
    private String domain;
    private RealAddr realAddr;
    private String tag;
    private String destStrategy;
    private long timestamp;

    public FakeContext(InetAddress fakeAddr, String domain, RealAddr realAddr, String tag, String destStrategy) {
        this.destStrategy = destStrategy;
        this.id= ThreadLocalRandom.current().nextInt();
        this.fakeAddr = fakeAddr;
        this.domain = domain;
        this.realAddr = realAddr;
        this.tag = tag;
        this.timestamp=System.currentTimeMillis()+(realAddr.ttl()*1000);
    }

    public NetAddr getNetAddr(int port){
        if (Objects.equals("ip", destStrategy)) {
            return new NetAddr(new InetSocketAddress(getRealAddr().addr(),port));
        } else {
            return new DomainNetAddr(domain, port);
        }
    }

    public long getId() {
        return id;
    }

    public InetAddress getFakeAddr() {
        return fakeAddr;
    }

    public String getDomain() {
        return domain;
    }

    public RealAddr getRealAddr() {
        return realAddr;
    }

    public String getTag() {
        return tag;
    }

    public long remainTimeMillis(){
        return timestamp-System.currentTimeMillis();
    }

    public boolean isAvailable(){
        return System.currentTimeMillis()<(timestamp-5000);
    }


}
