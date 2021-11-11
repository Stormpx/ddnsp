import io.crowds.proxy.ProxyOption;
import io.crowds.proxy.ProxyServer;
import io.crowds.proxy.services.socks.SocksOption;
import io.crowds.proxy.transport.vmess.Security;
import io.crowds.proxy.transport.vmess.User;
import io.crowds.proxy.transport.vmess.VmessOption;
import io.crowds.util.Hash;
import io.netty.buffer.Unpooled;
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
                            "kw;youtube;vmess",
                            "ew;www.google.com;vmess",
                            "kw;google;vmess",
                            "domain;pixiv.net;vmess",
                            "domain;pixiv.org;vmess",
                            "kw;pximg;vmess"
                        )
                ));

        server.start();

    }
}
