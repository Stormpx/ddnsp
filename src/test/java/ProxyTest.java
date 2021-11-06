import io.crowds.proxy.ProxyOption;
import io.crowds.proxy.ProxyServer;
import io.crowds.proxy.services.socks.SocksOption;
import io.crowds.util.Hash;
import io.netty.buffer.Unpooled;
import io.vertx.core.Vertx;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class ProxyTest {

    public static void main(String[] args) throws InterruptedException {

        Vertx vertx = Vertx.vertx();
        ProxyServer server = new ProxyServer(vertx.nettyEventLoopGroup())
                .setProxyOption(new ProxyOption()
                    .setSocks(new SocksOption().setHost("0.0.0.0").setPort(23452))
                );

        server.start();

    }
}
