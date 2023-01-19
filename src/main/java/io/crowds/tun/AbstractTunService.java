package io.crowds.tun;

import io.crowds.Platform;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.drasyl.channel.tun.TunAddress;
import org.drasyl.channel.tun.TunChannel;
import org.drasyl.channel.tun.TunChannelOption;
import org.drasyl.channel.tun.TunPacket;

import java.util.Objects;

public abstract class AbstractTunService implements TunService{

    protected TunOption option;

    private final TunRoute tunRoute;
    private Channel channel;

    public AbstractTunService(TunOption option) {
        this.option = option;
        if (Platform.isLinux()){
            this.tunRoute =new LinuxTunRoute(option.name,option.getIpcidr());
        }else{
            this.tunRoute =new WindowsTunRoute();
        }

    }



    protected Channel channel(){
        return channel;
    }

    @Override
    public Future<Void> start() {
//        if (!Platform.isLinux()){
//            return Future.failedFuture("currently only supports linux");
//        }
        Promise<Void> promise=Promise.promise();
        Bootstrap b = new Bootstrap()
                .group(new DefaultEventLoop())
                .option(TunChannelOption.TUN_MTU, Objects.requireNonNullElse(option.getMtu(),1500))
                .option(ChannelOption.RCVBUF_ALLOCATOR,new FixedRecvByteBufAllocator( Objects.requireNonNullElse(option.getMtu(),1500)))
                .channel(TunChannel.class)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<TunPacket>(false) {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, TunPacket msg) throws Exception {
                                handlePacket(msg);
                            }
                        });
                    }
                });
        ChannelFuture cf = b.bind(new TunAddress(option.name));
        cf.addListener(f->{
            if (!f.isSuccess()){
                promise.tryFail(f.cause());
                return;
            }
            this.channel = cf.channel();
            this.tunRoute.setup();
            promise.complete();
        });
        return promise.future();
    }

    protected abstract void handlePacket(TunPacket packet);

    protected void writePacket(TunPacket packet){
        channel.writeAndFlush(packet);
    }
}
