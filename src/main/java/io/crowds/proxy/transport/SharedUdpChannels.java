package io.crowds.proxy.transport;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.ProxyContext;
import io.crowds.util.Lambdas;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * UDP sessions in the full-cone shape: every target of the same source address shares a single
 * underlying channel.
 * <p>
 * Carrying several targets over one channel relies on the protocol itself (XUDP puts the target in
 * its New/Keep frames), and replies are dispatched by {@link UdpChannel} using
 * {@link DatagramPacket#sender()} to the {@link UdpEndPoint} bound to that target. A caller must
 * therefore only use this when the underlying protocol can multiplex several targets, otherwise it
 * has to stay on the per destination path of
 * {@link io.crowds.proxy.transport.proxy.AbstractProxyTransport}.
 * <p>
 * A channel lives as long as the underlying connection: closing one {@link UdpEndPoint} does not
 * close the channel, it is removed from the table when the channel itself is closed and recreated
 * by the next request.
 */
public class SharedUdpChannels {

    /**
     * Opens the channel for a source address, usually the createChannel of the transport itself.
     */
    public interface ChannelOpener {
        Future<Channel> open(EventLoop eventLoop, NetLocation netLocation) throws Exception;
    }

    private final Map<NetAddr, Future<UdpChannel>> channels;

    public SharedUdpChannels() {
        this.channels = new ConcurrentHashMap<>();
    }

    public Future<EndPoint> createEndPoint(ProxyContext proxyContext, ChannelOpener opener) {
        NetLocation netLocation = proxyContext.getNetLocation();
        EventLoop eventLoop = proxyContext.getEventLoop();
        NetAddr src = netLocation.getSrc();

        Future<UdpChannel> channelFuture = channels.computeIfAbsent(src, Lambdas.rethrowFunction(_ -> {
            Promise<UdpChannel> promise = eventLoop.newPromise();
            try {
                opener.open(eventLoop, netLocation)
                      .addListener((FutureListener<Channel>) f -> {
                          if (!f.isSuccess()) {
                              promise.tryFailure(f.cause());
                              return;
                          }
                          //a shared channel must never use closeIfEmpty: dropping the handler of one
                          //target must not affect the others
                          promise.trySuccess(new UdpChannel(f.get(), src.getAsInetAddr(), false));
                      });
            } catch (Exception e) {
                promise.tryFailure(e);
            }
            promise.addListener((FutureListener<UdpChannel>) f -> {
                if (!f.isSuccess()) {
                    channels.remove(src, promise);
                    return;
                }
                UdpChannel udpChannel = f.get();
                Consumer<DatagramPacket> fallbackPacketHandler = proxyContext.fallbackPacketHandler();
                if (fallbackPacketHandler != null) {
                    udpChannel.fallbackHandler(fallbackPacketHandler);
                }
                udpChannel.getChannel()
                          .closeFuture()
                          .addListener(_ -> channels.remove(src, promise));
            });
            return promise;
        }));
        //if the channel failed synchronously, computeIfAbsent has not put the promise into the map yet,
        //so the listener above has nothing to remove and the cleanup has to happen here
        if (channelFuture.isDone() && !channelFuture.isSuccess()) {
            channels.remove(src, channelFuture);
        }

        Promise<EndPoint> promise = eventLoop.newPromise();
        channelFuture.addListener((FutureListener<UdpChannel>) f -> {
            if (!f.isSuccess()) {
                promise.tryFailure(f.cause());
                return;
            }
            promise.trySuccess(new UdpEndPoint(f.get(), netLocation.getDst()));
        });
        return promise;
    }

}
