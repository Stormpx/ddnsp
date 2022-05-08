package io.crowds.proxy.select;

import io.crowds.proxy.*;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.proxy.transport.proxy.block.BlockProxyTransport;
import io.crowds.proxy.transport.proxy.direct.DirectProxyTransport;
import io.crowds.proxy.transport.proxy.shadowsocks.ShadowsocksOption;
import io.crowds.proxy.transport.proxy.shadowsocks.ShadowsocksTransport;
import io.crowds.proxy.transport.proxy.trojan.TrojanOption;
import io.crowds.proxy.transport.proxy.trojan.TrojanProxyTransport;
import io.crowds.proxy.transport.proxy.vmess.VmessOption;
import io.crowds.proxy.transport.proxy.vmess.VmessProxyTransport;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TransportProvider {
    private final static Logger logger= LoggerFactory.getLogger(TransportProvider.class);
    private final static String DEFAULT_TRANSPORT="direct";
    private final static String BLOCK_TRANSPORT="block";

    private ChannelCreator channelCreator;

    private Map<String, ProxyTransport> transportMap;

    private Map<String,TransportSelector> selectorMap;

    public TransportProvider(ChannelCreator channelCreator,List<ProtocolOption> protocolOptions,JsonArray selectors) {
        this.channelCreator = channelCreator;
        initTransport(protocolOptions);
        initSelector(selectors);
    }


    private void initTransport(List<ProtocolOption> protocolOptions){
        var map=new ConcurrentHashMap<String,ProxyTransport>();

        map.put(DEFAULT_TRANSPORT,new DirectProxyTransport(channelCreator));
        map.put(BLOCK_TRANSPORT,new BlockProxyTransport(channelCreator));

        if (protocolOptions!=null) {
            for (ProtocolOption protocolOption : protocolOptions) {
                if ("vmess".equalsIgnoreCase(protocolOption.getProtocol())) {
                    map.put(protocolOption.getName(), new VmessProxyTransport(channelCreator, (VmessOption) protocolOption));
                } else if ("ss".equalsIgnoreCase(protocolOption.getProtocol())) {
                    map.put(protocolOption.getName(), new ShadowsocksTransport(channelCreator, (ShadowsocksOption) protocolOption));
                } else if ("trojan".equalsIgnoreCase(protocolOption.getProtocol())){
                    map.put(protocolOption.getName(), new TrojanProxyTransport(channelCreator, (TrojanOption) protocolOption));
                }else if ("direct".equalsIgnoreCase(protocolOption.getProtocol())){
                    map.put(protocolOption.getName(),new DirectProxyTransport(channelCreator,protocolOption));
                }
            }
        }
        this.transportMap=map;
    }

    private void initSelector(JsonArray array){
        this.selectorMap=new ConcurrentHashMap<>();
        if (array==null||array.isEmpty()){
            return;
        }
        for (int i = 0; i < array.size(); i++) {
            JsonObject entries = array.getJsonObject(i);
            String name = entries.getString("name");
            String methodStr = entries.getString("method");
            Method method = Method.of(methodStr);
            if (name==null||name.isEmpty()){
                throw new IllegalArgumentException(String.format("selector[%d].name must == null",i));
            }
            if (methodStr==null||methodStr.isEmpty()){
                throw new IllegalArgumentException(String.format("selector[%d].method == null",i));
            }
            if (method==null){
                throw new IllegalArgumentException("unknown method: "+methodStr);
            }
            switch (method){
                case RR -> createRR(name,entries);
                case WRR -> createWRR(name,entries);
                case HASH -> createHash(name,entries);
                case RAND -> createRand(name,entries);
                case ROUTING -> createRouting(name,entries);
            }
        }
        List<String> refs = new RingDetector(this.selectorMap).searchCircularRef();
        if (!refs.isEmpty()){
            String refsStr = String.join("-", refs);
            logger.error("circular reference is not allowed. |-->{}-->|",refsStr);
            logger.error("                                   |---{}---|","-".repeat(refsStr.length()));
            throw new IllegalStateException("circular reference detected. ");
        }
    }

    private void readTag(JsonArray array, Consumer<String> consumer){
        if (array==null||array.isEmpty()){
            return;
        }
        for (int i = 0; i < array.size(); i++) {
            consumer.accept(array.getString(i));
        }
    }

    private void createRR(String name,JsonObject json){
        JsonArray tagArr = json.getJsonArray("tags");

        List<WRR.WNode> nodes=new ArrayList<>();
        readTag(tagArr,str->nodes.add(new WRR.WNode(1,str)));
        if (!nodes.isEmpty()) {
            selectorMap.put(name, new WRR(name, nodes));
        }
    }

    private void createWRR(String name,JsonObject json){
        JsonArray tagArr = json.getJsonArray("tags");
        List<WRR.WNode> nodes=new ArrayList<>();
       readTag(tagArr,str->{
           if (!str.contains(":")){
               nodes.add(new WRR.WNode(1,str));
           }else{
               try {
                   String[] strings = str.split(":",2);
                   int weight = Integer.parseInt(strings[0]);
                   nodes.add(new WRR.WNode(Math.max(weight, 0),strings[1]));
               } catch (NumberFormatException e) {

                   nodes.add(new WRR.WNode(1,str));
               }
           }
       });
        if (!nodes.isEmpty()) {
            selectorMap.put(name, new WRR(name, nodes));
        }
    }

    private void createRouting(String name, JsonObject json){
        JsonArray tagArr = json.getJsonArray("tags");
        List<String> rules=new ArrayList<>();
        readTag(tagArr, rules::add);
        selectorMap.put(name,new Routing(name,rules));
    }

    private void createRand(String name,JsonObject json){
        JsonArray tagArr = json.getJsonArray("tags");
        List<String> tags=new ArrayList<>();
        readTag(tagArr, tags::add);
        if (!tags.isEmpty()) {
            selectorMap.put(name, new Random(name, tags));
        }
    }

    private void createHash(String name,JsonObject json){
        JsonArray tagArr = json.getJsonArray("tags");
        List<String> tags=new ArrayList<>();
        readTag(tagArr, tags::add);

        if (!tags.isEmpty()) {
            JsonObject virtual = json.getJsonObject("virtual");
            Map<String,String> map=virtual==null?Map.of():
                    virtual.stream().filter(e->e.getValue() instanceof String).collect(Collectors.toMap(Map.Entry::getKey, e->e.getValue().toString()));
            selectorMap.put(name, new Hash(name,map, tags));
        }
    }



    private Transport fallback(String tag,String fall){
        ProxyTransport transport = transportMap.get(tag);
        assert transport!=null;
        return new Transport(transport, fall!=null?List.of(fall,tag):List.of(tag));
    }


    public Transport direct(){
        return fallback(DEFAULT_TRANSPORT,null);
    }


    public Transport getTransport(ProxyContext proxyContext){


        String tag = proxyContext.getFakeContext()!=null?proxyContext.getFakeContext().getTag():proxyContext.getTag();

        if (tag==null)
            return direct();

        if (transportMap.containsKey(tag)){
            return new Transport(transportMap.get(tag),List.of(tag));
        }

        TransportSelector selector = selectorMap.get(tag);
        if (selector==null){
            return fallback(DEFAULT_TRANSPORT,tag);
        }
        List<String> chain=new ArrayList<>();
        chain.add(tag);

        while (selector!=null){

            tag=selector.nextTag(proxyContext);
            if (tag==null){
                break;
            }
            chain.add(tag);

            ProxyTransport proxy = transportMap.get(tag);
            if (proxy!=null){
                return new Transport(proxy,chain);
            }

            selector=selectorMap.get(tag);

        }
        //fallback to direct
        chain.add(DEFAULT_TRANSPORT);
        return new Transport(direct().proxy(),chain);
    }



    public static class RingDetector {
        private Map<String,TransportSelector> selectorMap;

        public RingDetector(Map<String, TransportSelector> selectorMap) {
            this.selectorMap = selectorMap;
        }

        private Set<String> search(Set<String> path, String tag){
            path.add(tag);
            TransportSelector selector = selectorMap.get(tag);
            if (selector!=null){
                for (String s : selector.tags()) {
                    if (path.contains(s)){
                        return path;
                    }
                    var r=search(path,s);
                    if (!r.isEmpty())
                        return r;
                }
            }
            path.remove(tag);
            return Set.of();
        }

        public List<String> searchCircularRef(){

            if (selectorMap==null||selectorMap.isEmpty()){
                return List.of();
            }

            TreeMap<String,Integer> edges=new TreeMap<>();
            for (Map.Entry<String, TransportSelector> entry : selectorMap.entrySet()) {
                edges.putIfAbsent(entry.getKey(), 0);
                for (String tag : entry.getValue().tags()) {
                    edges.compute(tag,(k,v)->v==null?1:v+1);
                }
            }

            assert !edges.isEmpty();

            while (!edges.isEmpty()){

                var entry= edges.entrySet().stream().filter(e->e.getValue()==0).findAny().orElse(null);
                if (entry==null){
                    break;
                }
                assert entry.getValue()>=0;

                var selector=selectorMap.get(entry.getKey());
                if (selector!=null) {
                    for (String tag : selector.tags()) {
                        edges.computeIfPresent(tag,(k,v)->v-1);
                    }
                }

                edges.remove(entry.getKey());
            }

            if (!edges.isEmpty()){
                //ring
                var entry=edges.firstEntry();
                assert entry.getValue()!=0;
                var set=search(new LinkedHashSet<>(),entry.getKey());
                assert !set.isEmpty();
                return new ArrayList<>(set);
            }

            return List.of();
        }


    }


}
