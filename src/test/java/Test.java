import io.crowds.proxy.ChannelCreator;
import io.crowds.util.Mmdb;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.*;
import io.vertx.core.Vertx;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class Test {

    public static void main(String[] args) throws UnknownHostException {
        Vertx vertx = Vertx.vertx();
        Mmdb.initialize(vertx,5, TimeUnit.SECONDS);
        Mmdb.instance().load("Z:\\tmp\\Country.mmdb")
                .onSuccess(v->{
                    vertx.setTimer(10*1000,id->{
                        try {
                            System.out.println(Mmdb.instance().queryIsoCode(InetAddress.getByName("20.205.243.166")));

                            System.out.println(Mmdb.instance().queryIsoCode(InetAddress.getByName("69.63.190.26")));

                            System.out.println(Mmdb.instance().queryIsoCode(InetAddress.getByName("8.8.8.8")));

                            System.out.println(Mmdb.instance().queryIsoCode(InetAddress.getByName("114.114.114.114")));

                            System.out.println(Mmdb.instance().queryIsoCode(InetAddress.getByName("14.215.177.39")));

                            System.out.println(Mmdb.instance().queryIsoCode(InetAddress.getByName("8.134.50.24")));
                        } catch (UnknownHostException e) {
                            e.printStackTrace();
                        }

                        System.exit(0);
                    });

                });


    }
}
