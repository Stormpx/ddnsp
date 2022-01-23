package io.crowds.proxy.select;

import io.crowds.proxy.*;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.block.BlockProxyTransport;
import io.crowds.proxy.transport.direct.DirectProxyTransport;
import io.crowds.proxy.transport.shadowsocks.ShadowsocksOption;
import io.crowds.proxy.transport.shadowsocks.ShadowsocksTransport;
import io.crowds.proxy.transport.vmess.VmessOption;
import io.crowds.proxy.transport.vmess.VmessProxyTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TransportProvider {
    private final static Logger logger= LoggerFactory.getLogger(TransportProvider.class);
    private final static String DEFAULT_TRANSPORT="direct";
    private final static String BLOCK_TRANSPORT="block";

    private ChannelCreator channelCreator;

    private Map<String, ProxyTransport> transportMap;

    private Map<String,TransportSelector> selectorMap;

    public TransportProvider(ChannelCreator channelCreator,List<ProtocolOption> protocolOptions) {
        this.channelCreator = channelCreator;
        initProvider(protocolOptions);


    }


    private void initProvider(List<ProtocolOption> protocolOptions){
        var map=new ConcurrentHashMap<String,ProxyTransport>();

        map.put(DEFAULT_TRANSPORT,new DirectProxyTransport(channelCreator));
        map.put(BLOCK_TRANSPORT,new BlockProxyTransport(channelCreator));

        if (protocolOptions!=null) {
            for (ProtocolOption protocolOption : protocolOptions) {
                if ("vmess".equalsIgnoreCase(protocolOption.getProtocol())) {
                    map.put(protocolOption.getName(), new VmessProxyTransport(channelCreator, (VmessOption) protocolOption));
                } else if ("ss".equalsIgnoreCase(protocolOption.getProtocol())) {
                    map.put(protocolOption.getName(), new ShadowsocksTransport(channelCreator, (ShadowsocksOption) protocolOption));
                }
            }
        }
        this.transportMap=map;
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
