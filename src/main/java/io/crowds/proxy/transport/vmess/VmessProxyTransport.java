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

    private Map<Tuple4,VmessEndPoint> udpEpMap;

    public VmessProxyTransport(  ChannelCreator channelCreator,VmessOption  vmessOption) {
        super( channelCreator);
        this.vmessOption=vmessOption;
        this.udpEpMap=new ConcurrentHashMap<>();
    }

    @Override
    public String getTag() {
        return vmessOption.getName();
    }

    @Override
    public Future<EndPoint> createEndPoint(ProxyContext proxyContext) throws Exception {
        EventLoop eventLoop = proxyContext.getEventLoop();
        NetLocation netLocation = proxyContext.getNetLocation();

        if (netLocation.getTp()==TP.TCP) {
            VmessEndPoint endPoint = new VmessEndPoint(eventLoop, netLocation, vmessOption, channelCreator);
            return endPoint.init();
        }else{
            //udp connect reuse
            Tuple4 tuple4 = new Tuple4(netLocation.getSrc(), netLocation.getDest());
            VmessEndPoint vmessEndPoint = udpEpMap.computeIfAbsent(tuple4, Lambdas.rethrowFunction(k -> {
                VmessEndPoint endPoint = new VmessEndPoint(eventLoop, netLocation, vmessOption, channelCreator);
                endPoint.init()
                        .addListener(future -> {
                            if (!future.isSuccess())
                                udpEpMap.remove(tuple4);
                        });
                endPoint.closeFuture().addListener(future -> udpEpMap.remove(tuple4));
                return endPoint;
            }));
            vmessEndPoint.tryActive();
            return vmessEndPoint.getPromise();
        }
    }


    record Tuple4(NetAddr src,NetAddr dest){}

}
