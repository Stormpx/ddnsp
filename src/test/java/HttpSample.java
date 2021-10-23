import io.crowds.proxy.ChannelCreator;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class HttpSample {

    public static void main(String[] args) throws InterruptedException {
        NioEventLoopGroup group = new NioEventLoopGroup();
        ChannelCreator creator = new ChannelCreator(group);

        var cf=creator.createTcpChannel(new InetSocketAddress("127.0.0.1", 10240), new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                        System.out.println(msg.toString(StandardCharsets.UTF_8));
                    }
                });
            }
        });

        cf.await();
        assert cf.isSuccess();

        Channel httpChannel = cf.channel();

        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestEncoder());
        var header=new DefaultHttpHeaders()
                .add(HttpHeaderNames.HOST,"www.baidu.com")
                .add(HttpHeaderNames.ACCEPT,"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .add(HttpHeaderNames.CONNECTION,"close");
        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/",header);

        channel.writeOutbound(request);

        ByteBuf b=channel.readOutbound();

        System.out.println(b.toString(StandardCharsets.UTF_8));
        httpChannel.writeAndFlush(b);

        group.next().newPromise().sync();

    }
}
