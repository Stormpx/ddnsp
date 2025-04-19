package io.crowds.proxy.transport;


import io.crowds.proxy.Axis;
import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.ProxyContext;
import io.crowds.proxy.transport.proxy.chain.ChainOption;
import io.crowds.proxy.transport.proxy.chain.ChainProxyTransport;
import io.crowds.proxy.transport.proxy.direct.DirectProxyTransport;
import io.crowds.proxy.transport.proxy.shadowsocks.ShadowsocksOption;
import io.crowds.proxy.transport.proxy.shadowsocks.ShadowsocksTransport;
import io.crowds.proxy.transport.proxy.socks.SocksOption;
import io.crowds.proxy.transport.proxy.socks.SocksProxyTransport;
import io.crowds.proxy.transport.proxy.ssh.SshOption;
import io.crowds.proxy.transport.proxy.ssh.SshProxyTransport;
import io.crowds.proxy.transport.proxy.trojan.TrojanOption;
import io.crowds.proxy.transport.proxy.trojan.TrojanProxyTransport;
import io.crowds.proxy.transport.proxy.vless.VlessOption;
import io.crowds.proxy.transport.proxy.vless.VlessProxyTransport;
import io.crowds.proxy.transport.proxy.vmess.VmessOption;
import io.crowds.proxy.transport.proxy.vmess.VmessProxyTransport;
import io.crowds.proxy.transport.proxy.wireguard.WireguardOption;
import io.crowds.proxy.transport.proxy.wireguard.WireguardProxyTransport;
import io.netty.util.concurrent.Future;

public interface ProxyTransport {

    String getTag();

    Future<EndPoint> createEndPoint(ProxyContext proxyContext) throws Exception;


    static ProxyTransport create(Axis axis, ProtocolOption protocolOption){
        ChannelCreator channelCreator = axis.getChannelCreator();
        String protocol = protocolOption.getProtocol();
        if ("vmess".equalsIgnoreCase(protocol)) {
            return new VmessProxyTransport(channelCreator, (VmessOption) protocolOption);
        } else if ("ss".equalsIgnoreCase(protocol)) {
            return new ShadowsocksTransport(channelCreator, (ShadowsocksOption) protocolOption);
        } else if ("trojan".equalsIgnoreCase(protocol)){
            return new TrojanProxyTransport(channelCreator, (TrojanOption) protocolOption);
        } else if ("socks".equalsIgnoreCase(protocol)) {
            return new SocksProxyTransport(channelCreator, (SocksOption) protocolOption);
        } else if ("vless".equalsIgnoreCase(protocol)){
            return new VlessProxyTransport(channelCreator, (VlessOption) protocolOption);
        } else if ("ssh".equalsIgnoreCase(protocol)){
            return new SshProxyTransport(channelCreator, (SshOption) protocolOption);
        } else if ("wg".equalsIgnoreCase(protocol)){
            return new WireguardProxyTransport(axis, (WireguardOption) protocolOption);
        } else if ("chain".equalsIgnoreCase(protocol)){
            return new ChainProxyTransport(channelCreator, (ChainOption) protocolOption);
        } else if ("direct".equalsIgnoreCase(protocol)){
            return new DirectProxyTransport(channelCreator,protocolOption);
        }
        return null;
    }
}
