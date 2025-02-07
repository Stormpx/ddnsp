import io.crowds.Context;
import io.crowds.proxy.ProxyOption;
import io.crowds.proxy.ProxyServer;
import io.crowds.proxy.services.http.HttpOption;
import io.crowds.proxy.services.socks.SocksOption;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.proxy.shadowsocks.CipherAlgo;
import io.crowds.proxy.transport.proxy.shadowsocks.ShadowsocksOption;
import io.crowds.proxy.transport.proxy.trojan.TrojanOption;
import io.crowds.proxy.transport.proxy.vmess.VmessOption;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.net.*;
import java.util.Arrays;
import java.util.List;

public class ProxySver {

    public static void main(String[] args) throws InterruptedException, UnknownHostException {

        Vertx vertx = Vertx.vertx();
        ProxyServer server = new ProxyServer(new Context((VertxImpl) vertx));

        server.setProxyOption(new ProxyOption()
                        .setHttp(new HttpOption().setEnable(true).setHost("0.0.0.0").setPort(19999))
                    .setSocks(new SocksOption().setEnable(true).setHost("0.0.0.0").setPort(23452))
                        .setProxies(List.of(
                                new ProtocolOption().setName("direct_1").setProtocol("direct"),
                                new ProtocolOption().setName("direct_2").setProtocol("direct"),
                                new ProtocolOption().setName("direct_3").setProtocol("direct"),
                                new ProtocolOption().setName("direct_4").setProtocol("direct"),
                                new VmessOption()
//                                        .setNetwork("ws")
                                        .setName("vmess_1")
                                        .setProtocol("vmess"),
                                new ShadowsocksOption()
                                        .setCipher(CipherAlgo.AES_128_GCM)
                                        .setName("ss_1")
                                        .setProtocol("ss"),
                                new TrojanOption()
                                        .setName("tj_1")
                                        .setProtocol("trojan")

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
//                        "src-cidr;127.0.0.1/32;vmess_1",
                            "kw;youtube;block",
                            "ew;www.google.com;block",
                            "kw;google;block",
                            "domain;pixiv.net;block",
                            "domain;pixiv.org;block",
                            "kw;pximg;block",
                            "kw;baidu.com;direct"
                        )
                ));

        server.start();

    }
}
