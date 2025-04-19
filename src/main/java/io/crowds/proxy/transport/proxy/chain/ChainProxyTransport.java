package io.crowds.proxy.transport.proxy.chain;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.transport.Destination;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.proxy.transport.Transport;
import io.crowds.proxy.transport.proxy.AbstractProxyTransport;
import io.crowds.proxy.transport.proxy.ProxyTransportProvider;
import io.crowds.proxy.transport.proxy.direct.DirectProxyTransport;
import io.crowds.proxy.transport.proxy.wireguard.WireguardProxyTransport;
import io.crowds.util.AddrType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Future;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ChainProxyTransport extends AbstractProxyTransport {
    private ChainOption chainOption;

    private List<AbstractProxyTransport> proxyTransports;
    public ChainProxyTransport(ChannelCreator channelCreator, ChainOption chainOption) {
        super(channelCreator, chainOption);
        this.chainOption = chainOption;
    }

    public void initTransport(ProxyTransportProvider provider){
        List<NodeType> nodes = chainOption.getNodes();
        if (nodes==null||nodes.isEmpty()){
            throw new IllegalArgumentException("nodes is not configured");
        }
        this.proxyTransports=new ArrayList<>();
        for (NodeType node : nodes) {
            var pt = switch (node){
                case NodeType.Name(var name) -> provider.get(name);
                case NodeType.Option(var option) -> provider.create(option);
            };
            if (!(pt instanceof AbstractProxyTransport proxyTransport)){
                throw new IllegalArgumentException("unsupported proxyTransport node: "+pt.getTag());
            }
            if (proxyTransport instanceof DirectProxyTransport || proxyTransport instanceof WireguardProxyTransport){
                throw new IllegalArgumentException("unsupported proxyTransport node: "+pt.getTag());
            }
            if (proxyTransport==this){
                throw new IllegalArgumentException("self-referencing is not allowed.");
            }
            if (proxyTransport instanceof ChainProxyTransport chainProxyTransport) {
                if (chainProxyTransport.getProxyTransports().contains(this)) {
                    throw new IllegalArgumentException("circular reference detected in chain "+chainProxyTransport.getTag());
                }
            }

            this.proxyTransports.add(proxyTransport);
        }
    }

    List<AbstractProxyTransport> getProxyTransports() {
        return this.proxyTransports==null?List.of():this.proxyTransports;
    }

    @Override
    public String getTag() {
        return chainOption.getName();
    }



    @Override
    public Future<Channel> createChannel(EventLoop eventLoop, NetLocation netLocation, Transport delegate) throws Exception {
        ChainContext context = new ChainContext(eventLoop, netLocation, proxyTransports,delegate!=this.transport?delegate:null);
        return context.createChannel();
    }

}
