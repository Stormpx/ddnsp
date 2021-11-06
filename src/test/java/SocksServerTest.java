import io.crowds.proxy.Axis;
import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.services.socks.SocksOption;
import io.crowds.proxy.services.socks.SocksServer;
import io.netty.channel.nio.NioEventLoopGroup;

public class SocksServerTest {


    public static void main(String[] args) {
        NioEventLoopGroup group = new NioEventLoopGroup();
        ChannelCreator channelCreator = new ChannelCreator(group);
        var option=new SocksOption()
                .setEnable(true)
                .setHost("127.0.0.1")
                .setPort(12350);
        new SocksServer(option,new Axis(group))
                .start();
    }
}
