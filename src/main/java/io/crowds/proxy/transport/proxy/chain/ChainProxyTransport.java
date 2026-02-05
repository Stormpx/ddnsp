package io.crowds.proxy.transport.proxy.chain;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.TP;
import io.crowds.proxy.transport.Destination;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.proxy.transport.Transport;
import io.crowds.proxy.transport.proxy.AbstractProxyTransport;
import io.crowds.proxy.transport.proxy.ProxyTransportProvider;
import io.crowds.proxy.transport.proxy.direct.DirectProxyTransport;
import io.crowds.proxy.transport.proxy.wireguard.WireguardProxyTransport;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Future;

import java.util.*;
import java.util.stream.Collectors;

public class ChainProxyTransport extends AbstractProxyTransport<ChainOption> {

    private List<AbstractProxyTransport<?>> proxyTransports;
    private boolean skipCircularReferenceDetect;

    public ChainProxyTransport(ChannelCreator channelCreator, ChainOption chainOption) {
        super(channelCreator, chainOption);
    }

    private void detectedCircularReference(ProxyTransportProvider provider, List<String> path){
        String name = getTag();
        if (path.contains(name)){
            int index = path.indexOf(name);
            var p = path.subList(index,path.size()).stream().collect(Collectors.joining("->","|->","->|"));
            throw new ProxyChainException("circular reference detected: "+p);
        }
        List<NodeType> nodes = getProtocolOption().getNodes();
        if (nodes==null||nodes.isEmpty()){
            return;
        }
        path.add(name);
        for (NodeType node : nodes) {
            if (node instanceof NodeType.Name(var nodeName)){
                ProxyTransport proxyTransport = provider.get(nodeName);
                if (proxyTransport instanceof ChainProxyTransport chain){
                    chain.detectedCircularReference(provider, path);
                }
            }
        }
        path.remove(name);
        this.skipCircularReferenceDetect = true;
    }


    record ChainNode(NodeType nodeType, AbstractProxyTransport<?> proxyTransport){}

    private List<ChainNode> collectAndValidate(List<NodeType> nodes, ProxyTransportProvider provider){
        List<ChainNode> chainNodes = new ArrayList<>();

        for (NodeType node : nodes) {
            var pt = switch (node){
                case NodeType.Name(var name) -> provider.get(name);
                case NodeType.Option(var option) -> provider.create(option);
            };
            if (!(pt instanceof AbstractProxyTransport<?> proxyTransport)){
                throw new ProxyChainException("unsupported proxyTransport node: "+pt.getTag());
            }
            if (proxyTransport instanceof DirectProxyTransport || proxyTransport instanceof WireguardProxyTransport){
                throw new ProxyChainException("unsupported proxyTransport node: "+pt.getTag());
            }
            chainNodes.add(new ChainNode(node,proxyTransport));
        }
        return chainNodes;
    }

    private List<AbstractProxyTransport<?>> copyProxyTransports(List<ChainNode> chainNodes,ProxyTransportProvider provider){
        var proxyTransports = new ArrayList<AbstractProxyTransport<?>>();

        for (ChainNode chainNode : chainNodes) {
            var abstractProxyTransport = switch (chainNode.nodeType){
                case NodeType.Name(var nodeName) -> {
                    AbstractProxyTransport<?> proxyTransport = (AbstractProxyTransport<?>) provider.get(nodeName,true);
                    if (proxyTransport instanceof ChainProxyTransport chainProxyTransport){
                        chainProxyTransport.skipCircularReferenceDetect = true;
                        chainProxyTransport.initTransport(provider);
                    }
                    yield proxyTransport;
                }
                case NodeType.Option _ -> chainNode.proxyTransport;
            };
            proxyTransports.add(abstractProxyTransport);
        }
        return proxyTransports;
    }

    private void replaceTransports(List<AbstractProxyTransport<?>> proxyTransports){
        Transport previousTransport = Transport.provider(this::getTransport);

        for (int i = 0; i < proxyTransports.size(); i++) {
            var currentPt = proxyTransports.get(i);
            Transport transport = currentPt.getTransport();
            currentPt.setTransport(Transport.compose(transport, previousTransport));

            if (proxyTransports.getLast()!=currentPt){
                //not the last one
                var nextPt = proxyTransports.get(i+1);
                Destination remote = nextPt.getRemote(TP.TCP);
                var server = remote==null?null:remote.addr();
                previousTransport = new ProxyTransportTransport(server, currentPt);
            }

        }
    }

    public void initTransport(ProxyTransportProvider provider){
        List<NodeType> nodes = getProtocolOption().getNodes();
        if (nodes==null||nodes.isEmpty()){
            throw new ProxyChainException("nodes is not configured");
        }
        if (!skipCircularReferenceDetect) {
            detectedCircularReference(provider, new ArrayList<>());
        }

        List<ChainNode> chainNodes = collectAndValidate(nodes,provider);
        List<AbstractProxyTransport<?>> proxyTransports = copyProxyTransports(chainNodes, provider);
        replaceTransports(proxyTransports);
        this.proxyTransports = proxyTransports;


    }


    @Override
    public Future<Channel> createChannel(EventLoop eventLoop, NetLocation netLocation) throws Exception {
        return this.proxyTransports.getLast().createChannel(eventLoop,netLocation);
//        ChainContext context = new ChainContext(eventLoop, netLocation, proxyTransports,delegate!=this.transport?delegate:null);
//        return context.createChannel();
    }

}
