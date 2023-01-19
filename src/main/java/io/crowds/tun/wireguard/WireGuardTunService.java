package io.crowds.tun.wireguard;

import io.crowds.Platform;
import io.crowds.tun.AbstractTunService;
import io.crowds.lib.boringtun.Wg;
import io.crowds.util.Lambdas;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.drasyl.channel.tun.TunPacket;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class WireGuardTunService extends AbstractTunService {

    private EventLoopGroup eventLoopGroup;

    private List<WireGuardTunnel> tunnels;

    public WireGuardTunService(EventLoopGroup eventLoopGroup, WireGuardOption option) {
        super(option);
        this.eventLoopGroup=eventLoopGroup;
    }

    private Future<Channel> allocChannel() {
        DatagramChannel channel = Platform.getDatagramChannel();
        EventLoop eventLoop = eventLoopGroup.next();
        Promise<Channel> promise = eventLoop.newPromise();
        eventLoop.register(channel).addListener(bf->{
            if (!bf.isSuccess()){
                promise.tryFailure(bf.cause());
                return;
            }
            channel.bind(new InetSocketAddress(0)).addListener(f->{
                 if (!f.isSuccess()){
                     promise.tryFailure(f.cause());
                 }else{
                     promise.trySuccess(channel);
                 }
            });

        });


        return promise;
    }

    private io.vertx.core.Future<Void> start0(){
        var promise = io.vertx.core.Promise.<Void>promise();
        WireGuardOption wireGuardOption = (WireGuardOption) option;
        try {
            this.tunnels = wireGuardOption.getPeers().stream()
                    .map(Lambdas.rethrowFunction(peer->{
                        Channel channel = allocChannel().sync().get();
                        return new WireGuardTunnel(channel, wireGuardOption.getPrivateKey(), peer, Wg.nextIndex())
                                .packetHandler(this::writePacket);
                    }))
                    .collect(Collectors.toList());
            promise.complete();
        } catch (Exception e) {
            promise.tryFail(e);
        }

        return promise.future();
    }

    @Override
    public io.vertx.core.Future<Void> start() {
        return start0().compose(v-> super.start());
    }

    @Override
    protected void handlePacket(TunPacket packet) {
        InetAddress address = packet.destinationAddress();
        var optional = tunnels.stream().filter(it -> it.match(address)).min(Comparator.comparingInt(WireGuardTunnel::mask));
        if (optional.isPresent()){
            optional.get().write(packet.content());
        }else{
            ReferenceCountUtil.safeRelease(packet);
        }


    }

}
