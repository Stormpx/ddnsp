package io.crowds.proxy.transport.proxy;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.ProxyContext;
import io.crowds.proxy.transport.ProtocolOption;

/**
 * A transport that is always full-cone: every UDP session uses the shape where all targets of one
 * source address share a single underlying channel.
 * <p>
 * It is only a convenience wrapper around
 * {@link AbstractProxyTransport#fullConeSupport(ProxyContext)} and only holds for protocols
 * that can carry several targets over one channel. Protocols that are full-cone in some cases only
 * (vless/vmess with XUDP) should extend {@link AbstractProxyTransport} and override that method.
 */
public abstract class FullConeProxyTransport<OPT extends ProtocolOption> extends AbstractProxyTransport<OPT> {

    public FullConeProxyTransport(ChannelCreator channelCreator, OPT protocolOption) {
        super(channelCreator,protocolOption);
    }

    @Override
    protected boolean fullConeSupport(ProxyContext proxyContext) {
        return true;
    }
}
