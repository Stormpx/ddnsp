package io.crowds.proxy.transport.vmess;

import io.crowds.proxy.*;
import io.crowds.proxy.transport.EndPoint;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;

public class VmessProxyTransport extends AbstractProxyTransport {


    private VmessOption vmessOption;

    public VmessProxyTransport(VmessOption  vmessOption, EventLoopGroup eventLoopGroup, ChannelCreator channelCreator) {
        super(eventLoopGroup, channelCreator);
        this.vmessOption=vmessOption;
    }

    @Override
    public Future<EndPoint> createEndPoint(NetLocation netLocation) {
        //todo udp connect reuse support
        VmessEndPoint endPoint = new VmessEndPoint(netLocation, vmessOption, channelCreator);
        return endPoint.init();
    }
}
