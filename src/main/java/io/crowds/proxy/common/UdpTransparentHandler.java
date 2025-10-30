package io.crowds.proxy.common;

import io.crowds.proxy.Axis;
import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.DatagramOption;
import io.crowds.proxy.dns.FakeContext;
import io.crowds.proxy.dns.FakeDns;
import io.crowds.util.AddrType;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.FutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class UdpTransparentHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger logger = LoggerFactory.getLogger(UdpTransparentHandler.class);

    private final Axis axis;
    private final ChannelCreator channelCreator;

    public UdpTransparentHandler(Axis axis, ChannelCreator channelCreator) {
        super(false);
        this.axis = axis;
        this.channelCreator = channelCreator;
    }

    private boolean accept(InetSocketAddress destAddr){
        InetAddress dest = destAddr.getAddress();
        FakeDns fakeDns = axis.getFakeDns();
        if (fakeDns !=null&&fakeDns.isFakeIp(dest)){
            FakeContext fake = fakeDns.getFake(dest);
            return fake != null;
        }
        return true;
    }

    private InetSocketAddress getFakeAddress(InetSocketAddress address, AddrType addrType){
        if (!address.isUnresolved()){
            return address;
        }
        FakeDns fakeDns = axis.getFakeDns();
        if (fakeDns==null){
            return null;
        }
        FakeContext fakeContext = fakeDns.getFake(address.getHostString(), addrType);
        return fakeContext != null ? new InetSocketAddress(fakeContext.getFakeAddr(), address.getPort()) : null;
    }

    private io.netty.util.concurrent.Future<DatagramChannel> createForeignChannel(InetSocketAddress address)  {
        return channelCreator.createDatagramChannel(
                new DatagramOption().setBindAddr(address).setIpTransport(true),
                new BaseChannelInitializer().connIdle(300,(ch,idleStateEvent) -> ch.close()));
    }

    private void handleFallbackPacket(DatagramPacket packet)  {
        InetSocketAddress sender = packet.sender();
        if (sender==null){
            logger.warn("The sender of the packet is null, drop the packet");
            ReferenceCountUtil.safeRelease(packet);
            return;
        }
        if (sender.isUnresolved()) {
            var fakeAddr = getFakeAddress(sender, AddrType.of(packet.recipient()));
            if (fakeAddr==null){
                logger.warn("The fake address of the {} cannot be found. drop the packet",sender);
                ReferenceCountUtil.safeRelease(packet);
                return;
            }
            sender=fakeAddr;
        }
        InetSocketAddress finalSender = sender;
        createForeignChannel(sender)
                .addListener((FutureListener<DatagramChannel>) future -> {
                    if (!future.isSuccess()){
                        logger.error("Bind address:{} failed cause:{}", finalSender,future.cause().getMessage());
                        ReferenceCountUtil.safeRelease(packet);
                        return;
                    }
                    DatagramChannel datagramChannel= future.get();
                    datagramChannel.writeAndFlush(packet);
                });
    }



    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {

        InetSocketAddress recipient = msg.recipient();
        InetSocketAddress sender = msg.sender();
        if (logger.isDebugEnabled())
            logger.debug("udp data packet receive sender: {} recipient: {}",sender,recipient);
        if (recipient.getPort()==0){
            ReferenceCountUtil.safeRelease(msg);
            return;
        }
        if (!accept(recipient)){
            ReferenceCountUtil.safeRelease(msg);
            return;
        }
        createForeignChannel(recipient)
                .addListener((FutureListener<DatagramChannel>) future -> {
                    if (!future.isSuccess()){
                        logger.error("Bind address:{} failed cause:{}", recipient,future.cause().getMessage());
                        ReferenceCountUtil.safeRelease(msg);
                        return;
                    }
                    DatagramChannel datagramChannel= future.get();
                    axis.handleUdp0(datagramChannel,msg, this::handleFallbackPacket);
                });

    }

}