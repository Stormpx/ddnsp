package io.crowds.compoments.wireguard;

import io.crowds.lib.boringtun.BoringTun;
import io.crowds.lib.boringtun.wireguard_result;
import io.netty.channel.EventLoop;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class WireGuardTunnel implements Closeable {
    private final static Logger logger = LoggerFactory.getLogger(WireGuardTunnel.class);
    private final EventLoop eventLoop;
    private final PeerOption peer;
    private final BoringTun.Tunnel tunnel;

    private ScheduledFuture<?> timerFuture;

    private Consumer<ByteBuffer> packetReadHandler;
    private Consumer<ByteBuffer> packetWriteHandler;
    private Consumer<IOException> closeHandler;

    public WireGuardTunnel(EventLoop eventLoop, String privateKey, PeerOption peer) {
        this.eventLoop=eventLoop;
        this.peer=peer;
        this.tunnel = BoringTun.newTunnel(65535,privateKey,peer.publicKey(),peer.perSharedKey(),peer.keepAlive());
        this.timerFuture = eventLoop.scheduleAtFixedRate(this::oneTick, 100, 100,TimeUnit.MILLISECONDS);
    }

    public WireGuardTunnel packetReadHandler(Consumer<ByteBuffer> packetReadHandler) {
        this.packetReadHandler = packetReadHandler;
        return this;
    }

    public WireGuardTunnel packetWriteHandler(Consumer<ByteBuffer> packetWriteHandler) {
        this.packetWriteHandler = packetWriteHandler;
        return this;
    }

    public WireGuardTunnel closeHandler(Consumer<IOException> closeHandler) {
        this.closeHandler = closeHandler;
        return this;
    }

    private boolean isWriteToNetWork(MemorySegment result){
        int op = wireguard_result.op$get(result);
        return op==1;
    }


    private void handleWireGuardResult(MemorySegment result){
        int op = wireguard_result.op$get(result);
        int size = (int)wireguard_result.size$get(result);
//        logger.info("wireguard result op:{} size:{}",op,size);
        if (op==0)
            return;
        if (op==2){
            WireGuardError error = WireGuardError.valueOf(size);
            close0(new WireguardErrorException(error));
            return;
        }

        if (size>0){
            var dst = tunnel.getBuffer();
            var byteBuffer = dst.asSlice(0,size).asByteBuffer();
            switch (op){
                case 1-> {
                    var packetWriteHandler = this.packetWriteHandler;
                    if (packetWriteHandler!=null) {
                        packetWriteHandler.accept(byteBuffer);
                    }
                }
                case 4,6->{
                    var packetReadHandler = this.packetReadHandler;
                    if (packetReadHandler!=null){
                        packetReadHandler.accept(byteBuffer);
                    }
                }
            }
        }
    }


    public void oneTick(){
        if (tunnel==null){
            return;
        }
        var result = BoringTun.tick(tunnel);
        handleWireGuardResult(result);
    }

    public void writePacket(ByteBuffer buf){
        if (eventLoop.inEventLoop()){
            MemorySegment in = MemorySegment.ofBuffer(buf);
            try {
                var result = BoringTun.write(tunnel,in);
                handleWireGuardResult(result);
            }finally {
                ReferenceCountUtil.safeRelease(buf);
            }
        }else{
            eventLoop.execute(()-> writePacket(buf));
        }
    }

    public void readPacket(ByteBuffer buf){
        if (eventLoop.inEventLoop()){
            try {
                MemorySegment in = MemorySegment.ofBuffer(buf);
                var result = BoringTun.read(tunnel,in);
                handleWireGuardResult(result);
                while (isWriteToNetWork(result)){
                    result = BoringTun.read(tunnel,MemorySegment.NULL);
                    handleWireGuardResult(result);
                }
            } finally {
                ReferenceCountUtil.release(buf);
            }
        }else{
            eventLoop.execute(()-> readPacket(buf));
        }

    }

    public PeerOption peer(){
        return peer;
    }

    public boolean match(InetAddress address){
        return peer.allowedIp().isMatch(address.getAddress());
    }

    public int mask(){
        return peer.allowedIp().getMask();
    }

    private void close0(IOException e){
        if (this.timerFuture!=null){
            this.timerFuture.cancel(false);
        }
        if (this.tunnel!=null){
            BoringTun.free(tunnel);
        }

        if (this.closeHandler!=null){
            this.closeHandler.accept(e);
        }

    }


    @Override
    public void close() throws IOException {
        close0(null);
    }


}
