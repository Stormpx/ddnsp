import io.crowds.proxy.ProxyOption;
import io.crowds.proxy.ProxyServer;
import io.crowds.proxy.services.socks.SocksOption;
import io.crowds.proxy.transport.vmess.Security;
import io.crowds.proxy.transport.vmess.User;
import io.crowds.proxy.transport.vmess.VmessOption;
import io.crowds.util.Hash;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.vertx.core.Vertx;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.UUID;

public class ProxyTest {

    public static void main(String[] args) throws InterruptedException {

        Vertx vertx = Vertx.vertx();
        ProxyServer server = new ProxyServer(vertx.nettyEventLoopGroup());




        server.setProxyOption(new ProxyOption()
                    .setSocks(new SocksOption().setEnable(true).setHost("0.0.0.0").setPort(23452))
                .setRules(Arrays.asList(
                            "kw;youtube;block",
                            "ew;www.google.com;block",
                            "kw;google;block",
                            "domain;pixiv.net;block",
                            "domain;pixiv.org;block",
                            "kw;pximg;block"
                        )
                ));

        server.start();
//        InetSocketAddress address = new InetSocketAddress("116.29.89.106", 0);
//        InetSocketAddress address1 = new InetSocketAddress("116.29.89.106", 38270);
//        System.out.println(address.equals(address1));
        //        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
//        NioDatagramChannel channel = new NioDatagramChannel();
//        eventLoopGroup.register(channel);
//        InetSocketAddress address = new InetSocketAddress(53352);
//        ChannelFuture future = channel.bind(address).sync();
//        assert future.isSuccess();
//        System.out.println(future.channel().isActive());
//        channel.closeFuture().addListener(future1 -> {
//            System.out.println(channel.isActive()+"closed");
//        });
//        System.out.println("dwqewq");
//        channel.close().sync();
//        System.out.println(channel.isActive());
//
//        assert new NioDatagramChannel().bind(address).sync().isSuccess();
//        System.out.println("awewaewa");

    }
}
