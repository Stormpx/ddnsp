import io.crowds.proxy.ProxyServer;
import io.crowds.util.Hash;
import io.netty.buffer.Unpooled;
import io.vertx.core.Vertx;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class ProxyTest {

    public static void main(String[] args) throws InterruptedException {

        Vertx vertx = Vertx.vertx();
        ProxyServer server = new ProxyServer(vertx.nettyEventLoopGroup());
        server.start(new InetSocketAddress("0.0.0.0",23451));

    }
}
