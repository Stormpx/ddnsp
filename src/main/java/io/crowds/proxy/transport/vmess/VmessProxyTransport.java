package io.crowds.proxy.transport.vmess;

import io.crowds.proxy.*;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.util.Lambdas;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VmessProxyTransport extends AbstractProxyTransport  {


    private VmessOption vmessOption;


    public VmessProxyTransport(  ChannelCreator channelCreator,VmessOption  vmessOption) {
        super( channelCreator);
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

        VmessEndPoint endPoint = new VmessEndPoint(eventLoop, netLocation, vmessOption, channelCreator);
        return endPoint.init();
    }



}
