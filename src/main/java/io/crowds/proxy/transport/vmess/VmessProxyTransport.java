package io.crowds.proxy.transport.vmess;

import io.crowds.proxy.*;
import io.crowds.proxy.transport.EndPoint;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;

public class VmessProxyTransport extends AbstractProxyTransport implements TransportProvider {


    private VmessOption vmessOption;

    public VmessProxyTransport(VmessOption  vmessOption, EventLoopGroup eventLoopGroup, ChannelCreator channelCreator) {
        super(eventLoopGroup, channelCreator);
        this.vmessOption=vmessOption;
    }

    @Override
    public String getTag() {
        return vmessOption.getName();
    }

    @Override
    public Future<EndPoint> createEndPoint(ProxyContext proxyContext) throws Exception {
        EventLoop eventLoop = proxyContext.getEventLoop();
        NetLocation netLocation = proxyContext.getNetLocation();
        //todo udp connect reuse support
        VmessEndPoint endPoint = new VmessEndPoint(eventLoop,netLocation, vmessOption, channelCreator);
        return endPoint.init();
    }



}
