package io.crowds.proxy.select;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.ProxyContext;
import io.crowds.util.MurmurHash;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;

public class Hash extends TransportSelector{
//    private Map<String,String> tagMap;
    private Map<String,String> virtualMap;
    private final TreeMap<Long,String> hashRing;

    public Hash(String name,Map<String,String> virtualMap,List<String> tags) {
        super(name);
        Objects.requireNonNull(tags);
//        this.tagMap= Collections.emptyMap();
        this.virtualMap=virtualMap==null?Map.of():virtualMap;
        this.hashRing =new TreeMap<>();
        tags.forEach(str->this.hashRing.put(hash(str.getBytes(StandardCharsets.US_ASCII)),str));
        this.virtualMap.keySet().forEach(str->this.hashRing.put(hash(str.getBytes(StandardCharsets.US_ASCII)),str));
    }

    private long hash(byte[] content){
        return Integer.toUnsignedLong(MurmurHash.hash32(content,content.length));
    }


    @Override
    public List<String> tags() {
        return this.hashRing.values().stream().filter(Predicate.not(virtualMap::containsKey)).toList();
    }

    @Override
    public String nextTag(ProxyContext proxyContext) {
        NetAddr netAddr = proxyContext.getNetLocation().getSrc();
        byte[] srcIpBytes = netAddr.getAsInetAddr().getAddress().getAddress();
        long hash = hash(srcIpBytes);

        var entry = this.hashRing.ceilingEntry(hash);
        if (entry==null){
            entry=this.hashRing.firstEntry();
        }

        String tag = virtualMap.get(entry.getValue());

        return tag!=null?tag:entry.getValue();
    }
}
