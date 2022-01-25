package io.crowds.proxy.select;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.ProxyContext;

import java.util.*;

public class Hash extends TransportSelector{
//    private Map<String,String> tagMap;
    private Map<String,String> virtualMap;
    private final TreeMap<Integer,String> hashRing;

    public Hash(String name,Map<String,String> virtualMap,List<String> tags) {
        super(name);
        Objects.requireNonNull(tags);
        assert !tags.isEmpty();
//        this.tagMap= Collections.emptyMap();
        this.virtualMap=virtualMap==null?new HashMap<>():virtualMap;
        this.hashRing =new TreeMap<>();
        tags.forEach(str->this.hashRing.put(hash(str),str));
    }

    private int hash(Object obj){
        return Math.abs(Objects.hashCode(obj));
    }


    @Override
    public List<String> tags() {
        return new ArrayList<>(this.hashRing.values());
    }

    @Override
    public String nextTag(ProxyContext proxyContext) {
        NetAddr netAddr = proxyContext.getNetLocation().getSrc();
        int hash = Objects.hash(netAddr.getAsInetAddr().getAddress());

        var entry = this.hashRing.ceilingEntry(hash);
        if (entry==null){
            entry=this.hashRing.firstEntry();
        }

        String tag = virtualMap.get(entry.getValue());

        return tag!=null?tag:entry.getValue();
    }
}
