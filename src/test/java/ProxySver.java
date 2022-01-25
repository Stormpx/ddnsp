import io.crowds.proxy.ProxyOption;
import io.crowds.proxy.ProxyServer;
import io.crowds.proxy.services.socks.SocksOption;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.shadowsocks.ShadowsocksOption;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class ProxySver {

    public static void main(String[] args) throws InterruptedException {

        Vertx vertx = Vertx.vertx();
        ProxyServer server = new ProxyServer(vertx.nettyEventLoopGroup());

        server.setProxyOption(new ProxyOption()
                    .setSocks(new SocksOption().setEnable(true).setHost("0.0.0.0").setPort(23452))
                        .setProxies(List.of(
                                new ProtocolOption().setName("direct_1").setProtocol("direct"),
                                new ProtocolOption().setName("direct_2").setProtocol("direct"),
                                new ProtocolOption().setName("direct_3").setProtocol("direct"),
                                new ProtocolOption().setName("direct_4").setProtocol("direct"),
                                new ShadowsocksOption()
                                        .setName("ss-105")
                                        .setProtocol("ss")
                        ))
                        .setSelectors(new JsonArray()
                                .add(
                                        new JsonObject()
                                                .put("name","test_rr")
                                                .put("method","rr")
                                                .put("tags",new JsonArray().add("direct_1").add("direct_2").add("direct_3").add("direct_4"))
                                )
                                .add(
                                        new JsonObject()
                                                .put("name","test_wrr")
                                                .put("method","wrr")
                                                .put("tags",new JsonArray().add("0:direct_1").add("direct_2").add("5:direct_3").add("direct_4"))
                                )
                                .add(
                                        new JsonObject()
                                                .put("name","test_rand")
                                                .put("method","rand")
                                                .put("tags",new JsonArray().add("direct_1").add("direct_2").add("direct_3").add("direct_4"))
                                )
                                .add(
                                        new JsonObject()
                                                .put("name","test_hash")
                                                .put("method","hash")
                                                .put("tags",new JsonArray().add("direct_1").add("direct_2").add("direct_3").add("direct_4"))
                                )

                        )
                .setRules(Arrays.asList(
                            "kw;youtube;block",
                            "ew;www.google.com;block",
                            "kw;google;block",
                            "domain;pixiv.net;block",
                            "domain;pixiv.org;block",
                            "kw;pximg;block",
                            "kw;baidu.com;test_wrr"
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
