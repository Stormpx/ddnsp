package io.crowds.proxy.transport.proxy.localdns;

import io.crowds.dns.server.LocalMsgType;
import io.crowds.proxy.*;
import io.crowds.proxy.transport.*;
import io.crowds.util.Async;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.dns.*;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

public class DnsForwardProxyTransport implements ProxyTransport {
    public final static String TAG = "local-dns-forward";
    private final LocalAddress serverAddress;

    public DnsForwardProxyTransport(LocalAddress serverAddress) {
        this.serverAddress = serverAddress;
    }

    @Override
    public String getTag() {
        return TAG;
    }

    @Override
    public Future<EndPoint> createEndPoint(ProxyContext proxyContext) throws Exception {
        LocalAddress serverAddress = this.serverAddress;
        Promise<EndPoint> promise = proxyContext.getEventLoop().newPromise();
        NetLocation netLocation = proxyContext.getNetLocation();
        TP tp = netLocation.getTp();
        var cf = new Bootstrap()
                .group(proxyContext.getEventLoop())
                .channel(LocalChannel.class)
                .handler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    protected void initChannel(LocalChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                if (msg instanceof DatagramPacket packet){
                                    if (packet.sender()==null) {
                                        msg = new DatagramPacket(packet.content(), packet.recipient(), netLocation.getDst().getAsInetAddr());
                                    }
                                }
                                super.channelRead(ctx, msg);
                            }
                        });
                    }
                })
                .connect(serverAddress);
        Async.cascadeFailure(cf,promise,f->{
            Channel channel = cf.channel();

            if (tp ==TP.TCP){
                channel.writeAndFlush(new LocalMsgType.Stream(netLocation.getSrc().getAsInetAddr()));
                promise.trySuccess(new TcpEndPoint(channel));
            }else{
                channel.writeAndFlush(new LocalMsgType.Datagram());
                UdpChannel udpChannel = new UdpChannel(channel, netLocation.getSrc().getAsInetAddr(), true);
                udpChannel.fallbackHandler(proxyContext.fallbackPacketHandler());
                promise.trySuccess(new UdpEndPoint(udpChannel, netLocation.getDst()));
            }
        });
        return promise;
    }


}
