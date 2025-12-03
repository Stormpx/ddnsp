package io.crowds.proxy.services.xdp;

import io.crowds.Context;
import io.crowds.Platform;
import io.crowds.proxy.Axis;
import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.common.TcpTransparentHandler;
import io.crowds.proxy.common.UdpTransparentHandler;
import io.crowds.util.ChannelFactoryProvider;
import io.crowds.util.IPMask;
import io.crowds.util.Inet;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stormpx.net.RouteItem;
import org.stormpx.net.netty.PartialChannelOption;
import org.stormpx.net.netty.PartialDatagramChannel;
import org.stormpx.net.netty.PartialServerSocketChannel;
import org.stormpx.net.network.IfType;
import org.stormpx.net.network.NetworkParams;
import org.stormpx.net.socket.PartialSocketOptions;
import org.stormpx.net.util.*;

import java.net.*;
import java.util.*;

public class XdpServer {

    private static final Logger logger = LoggerFactory.getLogger(XdpServer.class);
    private static final String XDP_NETSPACE = "xdp-"+ UUID.randomUUID();
    private final XdpServerOption option;
    private final Axis axis;
    private final ChannelCreator channelCreator;

    private PartialServerSocketChannel tcpServer;
    private PartialDatagramChannel udpServer;

    public XdpServer(XdpServerOption option, Axis axis) {
        this.option = option;
        this.axis = axis;
        this.channelCreator =  new ChannelCreator(axis.getContext().getEventLoopGroup(), ChannelFactoryProvider.ofPartial(),axis.getContext().getVariantResolver());
        this.channelCreator.setPartialNetspace(XDP_NETSPACE);
    }


    private boolean acceptPacket(ProxyInfo info){
        IPPort dst = info.dst();
        IP dstIp = dst.ip();
        if (!dstIp.isUnicast()|| dstIp.isLinkLocal()|| dstIp.isUnspecified()){
            return false;
        }
        return true;
    }

    private Future<Void> startTcpServer(InetSocketAddress bindAddress){
        Promise<Void> promise=Promise.promise();
        Context context = axis.getContext();
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.channel(PartialServerSocketChannel.class)
                       .option(PartialChannelOption.NETSPACE,XDP_NETSPACE)
                       .option(PartialChannelOption.of(PartialSocketOptions.TRANSPARENT_PROXY), this::acceptPacket)
                       .option(PartialChannelOption.of(PartialSocketOptions.IP_TRANSPARENT),true);

        serverBootstrap
                .group(context.getAcceptor(),context.getEventLoopGroup())
                .childHandler(new ChannelInitializer<SocketChannel>(){
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new TcpTransparentHandler(axis));
                    }
                })
                .bind(bindAddress)
                .addListener(future -> {
                    if (future.isSuccess()) {
                        logger.info("start xdp tcp proxy server");
                        this.tcpServer = (PartialServerSocketChannel) future.get();
                        promise.complete();
                    }else {
                        logger.error("start xdp tcp proxy server failed cause:{}", future.cause().getMessage());
                        promise.tryFail(future.cause());
                    }
                })
        ;
        return promise.future();
    }

    private Future<Void> startUdpServer(InetSocketAddress bindAddress){
        Promise<Void> promise=Promise.promise();
        Context context = axis.getContext();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(context.getEventLoopGroup())
                 .channel(PartialDatagramChannel.class)
                 .option(PartialChannelOption.NETSPACE,XDP_NETSPACE)
                 .option(PartialChannelOption.of(PartialSocketOptions.TRANSPARENT_PROXY),this::acceptPacket)
                 .handler(new ChannelInitializer<>() {
                     @Override
                     protected void initChannel(Channel ch) throws Exception {
                         ch.pipeline().addLast(new UdpTransparentHandler(axis,channelCreator));
                     }
                 });
        ChannelFuture cf = bootstrap.bind(bindAddress);
        cf.addListener(f->{
            if (!f.isSuccess()){
                promise.tryFail(f.cause());
                return;
            }
            this.udpServer = (PartialDatagramChannel) cf.channel();
            logger.info("start xdp udp proxy server");
            promise.complete();
        });
        return promise.future();

    }

    private int getMTU(NetworkInterface nic) throws SocketException {
        return option.getMtu() == null ? nic.getMTU() : option.getMtu();
    }

    private Mac getMac(NetworkInterface nic) throws SocketException {
        if (option.getMac() == null) {
            byte[] hardwareAddress = nic.getHardwareAddress();
            if (hardwareAddress==null){
                throw new RuntimeException("XDP server: "+ "Interface '" + option.getIface() + "' has no MAC address. Please specify 'mac' in configuration");
            }
            return Mac.of(hardwareAddress);
        }
        return Mac.parse(option.getMac());
    }

    private List<IPMask> getAddress(NetworkInterface nic){
        if (option.getAddress() == null){
            var addresses = nic.getInterfaceAddresses().stream()
                    .filter(it->it.getAddress() instanceof Inet4Address)
                    .map(ifa->new IPMask(IP.of(ifa.getAddress().getAddress()),ifa.getNetworkPrefixLength()))
                    .toList();

            if (addresses.isEmpty()){
                throw new RuntimeException("XDP server: "+ "No IP address found on interface '" + option.getIface() + "'. Please specify 'address' in configuration");
            }
            return addresses;
        }
        return List.of(Inet.parseIPMask(option.getAddress()));
    }

    private void fillBypassIps(List<IP> localIps){
        List<String> bypassIps = Objects.requireNonNullElseGet(option.getOpt().getBypassIps(),ArrayList::new);
        for (IP localIp : localIps) {
            bypassIps.add(localIp.toString()+"/32");
        }
        option.getOpt().setBypassIps(bypassIps);
    }

    public Future<Void> start(){
        if (!Platform.isLinux()){
            return Future.failedFuture("currently only supports linux");
        }
        try {
            String iface = option.getIface();
            NetworkInterface networkInterface = Inet.findInterfaceByIdentity(iface);
            if (networkInterface==null){
                throw new RuntimeException("XDP server: "+ "Network interface '" + iface + "' not found");
            }

            var netStack = axis.getContext().getNetStack().getNetspace(XDP_NETSPACE);
            List<IPMask> addresses = getAddress(networkInterface);
            IPMask ipMask = addresses.getFirst();
            IP gateway = IP.parse(option.getGateway());
            Mac mac = getMac(networkInterface);
            int MTU = getMTU(networkInterface);
            logger.info("XDP iface: {} mac: {} mtu: {} address: {} gateway: {}", iface,mac,MTU,ipMask,gateway);
            fillBypassIps(addresses.stream().map(IPMask::ip).toList());
            SubNet subNet = new SubNet(ipMask.ip(), ipMask.mask());
            NetworkParams params = new NetworkParams();
            params.setMtu(MTU)
                    .setSubNet(subNet)
                    .setGateway(gateway)
                    .setMacAddress(mac)
                    .setVerifyChecksum(option.getOpt().isRxCheck())
                    .setIfType(IfType.ETHERNET)
                    .addIp(ipMask.ip());
            netStack.addNetwork(iface, params,()->new XdpIface(iface, option.getOpt()));
            netStack.addRoute(new RouteItem(new SubNet(IPv4.UNSPECIFIED,0), iface));

            InetSocketAddress bindAddress = new InetSocketAddress(InetAddress.getByAddress(IPv4.LOOPBACK.getBytes()),5474);
            return Future.any(startTcpServer(bindAddress),startUdpServer(bindAddress)).map((v)->null);
        }catch (UnknownHostException e) {
            return Future.failedFuture("Should not happen");
        }catch (Throwable e){
            return Future.failedFuture(e);
        }

    }

}
