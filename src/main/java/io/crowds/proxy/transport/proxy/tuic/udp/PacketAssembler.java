package io.crowds.proxy.transport.proxy.tuic.udp;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.transport.proxy.tuic.TuicCommand;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.EventLoop;
import io.netty.util.ReferenceCountUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PacketAssembler {

    private final EventLoop eventLoop;
    private final Map<Integer,Fragmentation> map = new HashMap<>();

    public PacketAssembler(EventLoop eventLoop) {
        this.eventLoop = eventLoop;
    }

    private final class Fragmentation{
        private final int pktId;
        private final ByteBuf[] fragments;
        private ScheduledFuture<?> recycleFuture;
        private NetAddr addr;
        private int fragmentCollected = 0;
        private int totalSize = 0;


        private Fragmentation(int pktId,int fragTotal) {
            this.pktId = pktId;
            this.fragments = new ByteBuf[fragTotal];
        }

        void put(TuicCommand.Packet fragment){
            if (fragments[fragment.fragId()]==null){
                fragments[fragment.fragId()] = fragment.data();
                fragmentCollected++;
                totalSize += fragment.data().readableBytes();
                if (addr ==null){
                    addr = fragment.addr();
                }
            }
        }

        boolean isComplete(){
            return fragmentCollected == fragments.length;
        }

        void recycle(){
            if (recycleFuture!=null)
                recycleFuture.cancel(false);
            for (ByteBuf buf : fragments) {
                ReferenceCountUtil.safeRelease(buf);
            }
        }
    }

    private ScheduledFuture<?> scheduleRecycle(Fragmentation fragmentation){
        return eventLoop.schedule(()->{
            if (map.remove(fragmentation.pktId,fragmentation)){
                fragmentation.recycleFuture = null;
                fragmentation.recycle();
            }
        },30, TimeUnit.SECONDS);
    }

    public Packet assemble(TuicCommand.Packet fragment, ByteBufAllocator alloc){
        int pktId = fragment.pktId();
        if (fragment.fragTotal()==1){
            return new Packet(pktId,fragment.addr(), fragment.data());
        }
        Fragmentation fragmentation = map.computeIfAbsent(pktId, k -> {
            Fragmentation it = new Fragmentation(pktId, fragment.fragTotal());
            it.recycleFuture = scheduleRecycle(it);
            return it;
        });
        fragmentation.put(fragment);
        if (!fragmentation.isComplete()) {
            return null;
        }
        ByteBuf buffer = alloc.compositeBuffer(fragmentation.totalSize);
        for (ByteBuf buf : fragmentation.fragments) {
            buffer.writeBytes(buf);
        }
        Packet packet = new Packet(pktId, fragmentation.addr, buffer);
        fragmentation.recycle();
        return packet;
    }

    public void recycleAll(){
        for (Fragmentation fragmentation : map.values()) {
            fragmentation.recycle();
        }
        map.clear();
    }
}
