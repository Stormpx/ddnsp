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


    private io.vertx.core.Future<Void> start0(){
        var promise = io.vertx.core.Promise.<Void>promise();
        WireGuardOption wireGuardOption = (WireGuardOption) option;
        try {
            this.tunnels = wireGuardOption.getPeers().stream()
                    .map(peer-> new WireGuardTunnel(eventLoopGroup.next(), wireGuardOption.getPrivateKey(), peer, Wg.nextIndex())
                            .packetHandler(this::writePacket))
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
        var optional = tunnels.stream().filter(it -> it.match(address)).max(Comparator.comparingInt(WireGuardTunnel::mask));
        if (optional.isPresent()){
            optional.get().write(packet.content());
        }else{
            ReferenceCountUtil.safeRelease(packet);
        }

    }

}
