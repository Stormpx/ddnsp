package io.crowds.proxy.transport.proxy.tuic;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.TP;
import io.crowds.proxy.common.IdleTimeoutHandler;
import io.crowds.proxy.transport.Destination;
import io.crowds.proxy.transport.TlsOption;
import io.crowds.proxy.transport.proxy.FullConeProxyTransport;
import io.crowds.util.Async;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.util.concurrent.ExecutionException;

public class TuicProxyTransport extends FullConeProxyTransport<TuicOption> {


    private final TuicOption tuicOption;
    private final Destination destination;
    private final EventLoop eventLoop;

    private TuicConnection connection;
    private volatile Future<TuicConnection> connectionFuture;

    public TuicProxyTransport(ChannelCreator channelCreator, TuicOption tuicOption) {
        this.tuicOption = tuicOption;
        this.destination = new Destination(NetAddr.of(tuicOption.getAddress()),TP.UDP);
        this.eventLoop = channelCreator.getEventLoopGroup().next();
        if (tuicOption.getTls()==null){
            tuicOption.setTls(new TlsOption().setEnable(true));
        }else if (!tuicOption.getTls().isEnable()){
            tuicOption.getTls().setEnable(true);
        }
        super(channelCreator, tuicOption);
    }

    @Override
    public String getTag() {
        return tuicOption.getName();
    }

    @Override
    public Destination getRemote(TP tp) {
        return destination;
    }


    public Future<TuicConnection> getConnection(){
        Promise<TuicConnection> promise = eventLoop.newPromise();

        eventLoop.execute(()->{
            if (connection != null) {
                if (!connection.isActive()){
                    connection = null;
                    connectionFuture = null;
                }
                promise.setSuccess(connection);
                return;
            }
            if (connectionFuture!=null){
                connectionFuture.addListener(Async.cascade(promise));
                return;
            }
            try {

                Async.cascadeFailure(transport.openChannel(eventLoop, destination, null),promise,f->{
                    Channel channel = f.get();
                    channel.attr(IdleTimeoutHandler.IGNORE_IDLE_FLAG);
                    this.connection = new TuicConnection(channel, tuicOption.getUser(), destination.addr(), tuicOption.getUdpMode());
                    TlsOption tls = tuicOption.getTls();
                    this.connection.setTlsInsecure(tls.isAllowInsecure());
                    if (tls.getAlpn()!=null){
                        this.connection.setAlpn(tls.getAlpn().toArray(String[]::new));
                    }
                    this.connectionFuture = promise;
                    this.connection.closeFuture().addListener(_->{
                        this.connection = null;
                        this.connectionFuture = null;
                    });
                    promise.setSuccess(connection);
                });
            } catch (Exception e) {
                promise.tryFailure(e);
            }
        });

        return promise;
    }

    private Future<Channel> deriveChannel(EventLoop eventLoop,NetLocation netLocation){
        Promise<Channel> promise = eventLoop.newPromise();

        Async.cascadeFailure(getConnection(),promise,f->{
            try {
                TuicConnection tuicConnection = f.get();
                if (!tuicConnection.isActive()){
                    promise.tryFailure(new IllegalStateException("Tuic connection inactive"));
                    return;
                }
                Future<Channel> future;
                if (netLocation.getTp()==TP.TCP){
                    future = tuicConnection.connect(netLocation.getDst());
                }else{
                    future = tuicConnection.associate();
                }
                Async.cascadeFailure(future,promise,cf->{
                    Channel channel = cf.get();
                    int connIdle = tuicOption.getConnIdle();
                    if (connIdle>0 || netLocation.getTp()==TP.UDP) {
                        channel.pipeline().addLast(new IdleTimeoutHandler(connIdle>0?connIdle:120,null));
                    }
                    promise.setSuccess(channel);
                });
            } catch (InterruptedException | ExecutionException e) {
                promise.tryFailure(e);
            }
        });


        return promise;
    }


    @Override
    public Future<Channel> createChannel(EventLoop eventLoop, NetLocation netLocation) throws Exception {
        return deriveChannel(eventLoop,netLocation);
    }
}
