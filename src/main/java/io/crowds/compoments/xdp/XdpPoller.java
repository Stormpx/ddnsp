package io.crowds.compoments.xdp;

import io.crowds.lib.Errno;
import io.crowds.lib.unix.Poll;
import io.crowds.lib.unix.PollFd;
import io.crowds.lib.unix.Unix;
import io.crowds.lib.xdp.*;
import io.crowds.lib.xdp.ffi.IfXdp;
import io.crowds.lib.xdp.ffi.XdpDesc;
import io.crowds.lib.xdp.ffi.XskTxMetadata;
import io.crowds.util.Native;
import io.netty.util.internal.shaded.org.jctools.queues.MpscUnboundedArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stormpx.net.buffer.ByteArray;
import top.dreamlike.panama.generator.proxy.ErrorNo;
import top.dreamlike.panama.generator.proxy.MemoryLifetimeScope;
import top.dreamlike.panama.generator.proxy.NativeArray;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class XdpPoller {

    private static final Logger logger = LoggerFactory.getLogger(XdpPoller.class);
    private static final Thread.Builder THREAD_BUILDER = Thread.ofPlatform().name("xdp-poller-",0);
    private static final int BATCH_SIZE = 64;
    private static final int REFILL_SIZE = 64;
    private static final double LOW_WATER_MARK_FACTOR = 0.25;
    private static final double HIGH_WATER_MARK_FACTOR = 0.75;

    private final UmemBufferPoll bufferPoll;
    private final List<XskContext> xskCtxs;
    private final List<BpfRingBuffer> ringBuffers = new CopyOnWriteArrayList<>();

    private final Poller poller;

    private final Map<Integer,Runnable> eventHandler = new ConcurrentHashMap<>();

    private final LocalBuffer localBuffer = new LocalBuffer();

    private volatile Thread boundThread;
    private final CompletableFuture<Void> startedFuture = new CompletableFuture<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final CompletableFuture<Void> closedFuture = new CompletableFuture<>();

    private int egressCounter = -1;
    private final Queue<TxDesc> egressQueue = new MpscUnboundedArrayQueue<>(4096);
    private final XdpIngressHandler ingressHandler;

    private short interestEvents = Poll.POLLIN;

    public XdpPoller(UmemBufferPoll bufferPoll, List<XdpSocket> sockets,boolean busyPoll, XdpIngressHandler ingressHandler) {
        this.bufferPoll = bufferPoll;
        this.xskCtxs = sockets.stream().map(XskContext::new).toList();
        this.ingressHandler = ingressHandler;
        this.poller = busyPoll?new BusyPoller():new SysPoller();
    }

    class LocalBuffer{
        private final int localBlocksReversed = 32;
        private final List<Block> localBlocks = new ArrayList<>();
        private int blockIndex = 0;

        private void freeBlocks(){
            var availBlocks = this.localBlocks.size()-1-this.blockIndex;
            var nbFree = availBlocks-this.localBlocksReversed;
            for (int i = 0; i < nbFree; i++) {
                Block block = this.localBlocks.removeLast();
                bufferPoll.put(block);
            }
        }

        private boolean allocBlock(){
            Block block = bufferPoll.get();
            if (block==null){
                return false;
            }
            this.localBlocks.add(block);
            return true;
        }

        private int freeFrames(){
            if (this.localBlocks.isEmpty()){
                return 0;
            }
            assert this.blockIndex<this.localBlocks.size();
            Block block = this.localBlocks.get(blockIndex);
            int free = block.size();

            for (int i = this.blockIndex+1; i < this.localBlocks.size(); i++) {
                block = this.localBlocks.get(blockIndex);
                free+=block.size();
            }

            return free;
        }

        private int reverseFrames(int nb){
            int freeFrames = freeFrames();
            while (freeFrames<nb){
                if (!allocBlock()){
                    break;
                }
                freeFrames+=this.localBlocks.getLast().size();
            }
            return Math.min(freeFrames,nb);
        }

        private long getAddr(boolean alloc){
            if (this.localBlocks.isEmpty()){
                if (!allocBlock()){
                    return -1;
                }
                this.blockIndex = 0;
            }
            assert this.blockIndex<this.localBlocks.size();

            Block block = this.localBlocks.get(blockIndex);
            if (!block.isEmpty()){
                return block.poll();
            }
            if (this.blockIndex >= this.localBlocks.size() - 1) {
                if (!alloc || !allocBlock()) {
                    return -1;
                }
            }
            this.blockIndex+=1;
            return getAddr(alloc);

        }

        private void putAddr(long addr){
            assert this.blockIndex<this.localBlocks.size();
            Block block = this.localBlocks.get(blockIndex);
            if (block.isFull()){
                assert this.blockIndex>0;
                this.blockIndex-=1;
                putAddr(addr);
                return;
            }
            addr = bufferPoll.umem().fixAddr(addr);
            block.push(addr);
        }
    }

    class XskContext {
        private final XdpSocket socket;
        private int lastFill = 0;
        private int lastReceived = 0;
        private boolean refill=false;

        XskContext(XdpSocket socket) {
            this.socket = socket;
        }

        private void kickRx(){
            MemoryLifetimeScope.auto().active(()->{
                Unix.INSTANCE.recvfrom(socket.fd(), MemorySegment.NULL, 0, Unix.MSG_DONTWAIT, MemorySegment.NULL, MemorySegment.NULL);
            });
        }

        private int doFill(int nb,MemorySegment idxPtr){
            XskProdRing fq = this.socket.fill();
            if (nb==0||fq.reserve(nb,idxPtr)==0){
                //should not happen
                return 0;
            }
            var idx = idxPtr.get(ValueLayout.JAVA_INT,0);
            int i = 0;
            for (; i < nb; i++) {
                long addr = localBuffer.getAddr(true);
                if (addr==-1){
                    break;
                }
                fq.addr(idx+i,addr);
            }
            fq.submit(i);
            if (fq.needsWakeup()){
                kickRx();
            }
//            logger.debug("fill {} to fd:{} fq",i,this.socket.fd());
            return i;
        }

        void fillRing(){
            XskProdRing fq = this.socket.fill();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment idxPtr = arena.allocate(ValueLayout.JAVA_INT);

                int nbFree = fq.nbFree(fq.ring().getSize());
                int nbUsing = fq.ring().getSize() - nbFree;
                if (!this.refill){
                    int lowWaterMark = (int) (fq.ring().getSize() * LOW_WATER_MARK_FACTOR);
                    if (nbUsing < lowWaterMark){
                        int fillSize = Math.min(nbFree,REFILL_SIZE);
                        fillSize = Math.min(localBuffer.reverseFrames(fillSize),fillSize);
                        fillSize = doFill(fillSize,idxPtr);

                        this.lastFill = Math.max(1,fillSize);
                        this.lastReceived = 0;
                        this.refill=true;
                    }
                    return;
                }
                int lastReceived = this.lastReceived;
                int lastFill = this.lastFill;
                int refillSize = REFILL_SIZE * ((lastReceived / lastFill)+1);
                refillSize = localBuffer.reverseFrames(refillSize);
                refillSize = Math.min(nbFree,refillSize);
                if (refillSize<=0){
                    return;
                }
                refillSize = doFill(refillSize,idxPtr);
                this.lastFill = Math.max(1,refillSize);
                this.lastReceived = 0;
                int highWaterMark = (int) (fq.ring().getSize() * HIGH_WATER_MARK_FACTOR);
                if (nbUsing+refillSize >= highWaterMark){
                    this.refill=false;
                }

            }
        }

        int receive(){
            XdpIngressHandler ingressHandler = XdpPoller.this.ingressHandler;
            assert ingressHandler!=null;
            int received = 0;
            Umem umem = socket.umemInfo();
            XskConsRing rx = socket.rx();
            List<Long> addrs = new ArrayList<>();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment idxPtr = arena.allocate(ValueLayout.JAVA_INT);
                int nPkt = rx.peek(rx.ring().getSize(),idxPtr);
                var idx = idxPtr.get(ValueLayout.JAVA_INT,0);
                for (int i = 0; i < nPkt; i++) {
                    XdpDesc desc = rx.desc(idx + i);
                    long umemAddr = desc.getUmemAddr();
                    long dataAddr = desc.getDataAddr();
                    int len = desc.getLen();
                    MemorySegment data = umem.getData(umemAddr);
                    ingressHandler.handle(new RxDesc(data,dataAddr-umemAddr,len));
                    if (!ingressHandler.isZeroCopy()) {
                        addrs.add(umemAddr);
                    }
                }
                rx.release(nPkt);
                received+=nPkt;
                this.lastReceived+=nPkt;
            }
            ingressHandler.complete();
            for (Long addr : addrs) {
                localBuffer.putAddr(addr);
            }
            return received;
        }

        int releaseComp() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment idxPtr = arena.allocate(ValueLayout.JAVA_INT);
                XskConsRing cq = socket.comp();
                int nPkt = cq.peek(cq.ring().getSize(), idxPtr);
                var idx = idxPtr.get(ValueLayout.JAVA_INT,0);
                for (int i = 0; i < nPkt; i++) {
                    long addr = cq.addr(idx + i);
                    localBuffer.putAddr(addr);
                }
                cq.release(nPkt);
                return nPkt;
            }

        }

        void kickTx(){
            MemoryLifetimeScope.auto()
                    .active(()->{
                        long ret = Unix.INSTANCE.sendto(socket.fd(), MemorySegment.NULL, 0, Unix.MSG_DONTWAIT, MemorySegment.NULL, 0);
                        var errno = ErrorNo.getCapturedError().errno();
                        if (ret>=0 || errno==Errno.ENOBUFS || errno==Errno.EAGAIN || errno == Errno.EBUSY || errno == Errno.ENETDOWN){
                            return;
                        }
                        logger.error("kick socket tx: {} failed: {}",socket.fd(),Unix.INSTANCE.strError(errno));
                    });

        }

        TxResult transmit(){
            Queue<TxDesc> egressQueue = XdpPoller.this.egressQueue;
            if (egressQueue.isEmpty()){
                return new TxResult(0,false);
            }
            int chunkSize = socket.umemInfo().chunkSize();
            XskProdRing txRing = socket.tx();

            try (Arena arena = Arena.ofConfined()){
                var idxPtr = arena.allocate(ValueLayout.JAVA_INT);

                int egressSize = egressQueue.size();
                egressSize = Math.min(egressSize,BATCH_SIZE);
                egressSize = Math.min(txRing.nbFree(egressSize),egressSize);
                int reverseFrames = localBuffer.reverseFrames(egressSize);
                if (reverseFrames==0){
                    return new TxResult(0,true);
                }
                egressSize = Math.min(reverseFrames,egressSize);
                if (egressSize==0||txRing.reserve(egressSize,idxPtr)==0){
                    //socket tx ring dose not enough space
                    return new TxResult(0,false);
                }
                int idx = idxPtr.get(ValueLayout.JAVA_INT, 0);

                int nb = 0;
                for (int i = 0; i < egressSize; i++) {
                    long addr = localBuffer.getAddr(true);
                    if (addr==-1){
                        break;
                    }

                    XdpDesc desc = txRing.desc(idx++);

                    MemorySegment buffer = socket.umemInfo().getData(addr);
                    TxDesc txDesc = egressQueue.poll();
                    assert txDesc !=null;
                    ByteArray data = txDesc.data();

                    if (txDesc.isFillMeta()){
                        long txMetadataLen = Native.getLayout(XskTxMetadata.class).byteSize();
                        if (chunkSize - txMetadataLen < data.length()){
                            idx--;
                            continue;
                        }
                        XskTxMetadata txMetadata = Native.as(buffer.asSlice(0,txMetadataLen).fill((byte) 0), XskTxMetadata.class);
                        long flags = 0;
                        if (txDesc.isRequestChecksum()){
                            var csumRequest = txMetadata.getUnion().getRequest();
                            csumRequest.setCsum_start((short) txDesc.csumStart());
                            csumRequest.setCsum_offset((short) txDesc.csumOffset());
                            flags|=IfXdp.XDP_TXMD_FLAGS_CHECKSUM;
                        }
                        if (txDesc.isRequestTimestamp()){
                            flags|=IfXdp.XDP_TXMD_FLAGS_TIMESTAMP;
                        }
                        txMetadata.setFlags(flags);
                        desc.setOptions(desc.getOptions() | IfXdp.XDP_TX_METADATA);

                        addr += txMetadataLen;
                        buffer = buffer.asSlice(txMetadataLen);

                    }


                    ByteArray.wrap(buffer.asByteBuffer()).setBuffer(0, data,0, data.length());

                    desc.setAddr(addr);
                    desc.setLen(data.length());

                    nb+=1;
                }

                txRing.submit(nb);
                if (txRing.needsWakeup()){
                    kickTx();
                }
                return new TxResult(nb,false);
            }

        }
    }

    private XskContext nextEgressSocket(){
        var seq = ++egressCounter;
        return this.xskCtxs.get( seq % this.xskCtxs.size());
    }

    record TxResult(int nb, boolean oom){}

    private TxResult processEgressQueue(){
        int sockets = this.xskCtxs.size();
        int nb = 0;
        boolean oom = false;
        while (!this.egressQueue.isEmpty()&&!oom&&sockets>0){
            XskContext xskContext = nextEgressSocket();
            TxResult result = xskContext.transmit();
//            logger.info("nb: {} oom: {}",result.nb,result.oom);
            if (result.nb==0){
                sockets--;
            }
            nb+= result.nb;
            oom = result.oom;
        }
        return new TxResult(nb,oom);
    }


    private void setup(){
        Map<Integer, Runnable> eventHandlers = this.eventHandler;
        for (XskContext ctx : this.xskCtxs) {
            eventHandlers.put(ctx.socket.fd(),ctx::receive);
        }
        for (BpfRingBuffer rb : this.ringBuffers) {
            eventHandlers.put(rb.fd(), rb::consume);
        }

        for (XskContext ctx : this.xskCtxs) {
            ctx.fillRing();
        }
        startedFuture.complete(null);
    }

    private void xdpPoll(){
        while (!this.closed.get()){
            this.poller.poll();
        }
        this.eventHandler.clear();
        this.poller.close();
        this.closedFuture.complete(null);
    }

    public CompletableFuture<Void> start(){
        if (this.closed.get()){
            throw new IllegalStateException("Poller already closed");
        }
        if (this.boundThread!=null){
            return startedFuture;
        }
        this.boundThread = THREAD_BUILDER.start(()->{
            setup();
            xdpPoll();
        });
        return startedFuture;
    }

    public void addRingBuffer(BpfRingBuffer ringBuffer){
        this.ringBuffers.add(ringBuffer);
        if (this.boundThread!=null) {
            this.eventHandler.put(ringBuffer.fd(), ringBuffer::consume);
        }
    }

    public void transmitData(TxDesc data){
        if (this.egressQueue.add(data)){
            this.poller.wakeup(1);
        }
    }

    public CompletableFuture<Void> stop(){
        if (this.closed.compareAndSet(false,true)){
            this.poller.wakeup(-1);
        }
        return this.closedFuture;
    }

    interface Poller{

        void poll();

        void wakeup(int code);

        void close();
    }

    class SysPoller implements Poller{

        private final int[] wakeupFd;
        private final AtomicBoolean wakeup = new AtomicBoolean(false);

        public SysPoller() {
            this.wakeupFd = Unix.INSTANCE.pipe(Unix.O_DIRECT|Unix.O_NONBLOCK);
            eventHandler.put(this.wakeupFd[0],this::drainWakeupMessage);
        }

        private NativeArray<PollFd> buildPollFds(Arena arena){
            NativeArray<PollFd> pollFds = Native.structArrayAlloc(arena, PollFd.class, 1 + xskCtxs.size()+ringBuffers.size());
            int index = 1;
            for (BpfRingBuffer ringBuffer : ringBuffers) {
                PollFd pollFd = pollFds.get(index++);
                pollFd.setFd(ringBuffer.fd());
                pollFd.setEvents((short) Poll.POLLIN);
            }
            for (XskContext xskCtx : xskCtxs) {
                PollFd pollFd = pollFds.get(index++);
                pollFd.setFd(xskCtx.socket.fd());
                pollFd.setEvents(interestEvents);
            }
            PollFd pollFd = pollFds.getFirst();
            pollFd.setFd(this.wakeupFd[0]);
            pollFd.setEvents((short) Poll.POLLIN);

            return pollFds;
        }

        @Override
        public void poll() {
            try (Arena arena = Arena.ofConfined()){
                var pollFds = buildPollFds(arena);
                int poll = MemoryLifetimeScope.of(arena).active(()-> Poll.INSTANCE.poll(pollFds, pollFds.size(), 100));
                if (poll<0){
                    logger.error("Poll return Error: {}",Unix.INSTANCE.strError(ErrorNo.getCapturedError().errno()));
                    return;
                }
                if (poll==0){
                    return;
                }
                boolean outEvent = false;
                Set<Integer> readableFds = new HashSet<>();
                for (PollFd pollFd : pollFds) {
                    int fd = pollFd.getFd();
                    short revents = pollFd.getRevents();

                    if ((revents&Poll.POLLOUT)!=0){
                        outEvent = true;
                    }
                    if ((revents&Poll.POLLIN)!=0){
                        try {
                            Runnable runnable = eventHandler.get(fd);
                            if (runnable!=null){
                                readableFds.add(fd);
                                runnable.run();
                            }
                        } catch (Exception e) {
                            logger.error("Error occurred while processing the read event fd: {}", fd,e);
                        }
                    }

                }

                for (XskContext ctx : xskCtxs) {
                    ctx.releaseComp();
                }
                if (outEvent||this.wakeup.get()){
                    interestEvents &= ~Poll.POLLOUT;
                    TxResult result = processEgressQueue();
                    this.wakeup.set(false);
                    if (!result.oom){
                        processEgressQueue();
                    }
                    if (!egressQueue.isEmpty()){
                        interestEvents |= Poll.POLLOUT;
                    }
                }
                for (XskContext xskCtx : xskCtxs) {
                    if (readableFds.contains(xskCtx.socket.fd())) {
                        xskCtx.fillRing();
                    }
                }
                localBuffer.freeBlocks();
            } catch (Throwable e) {
                logger.error("Error occurred while xdp poll",e);
            }
        }


        private void drainWakeupMessage(){
            int fd = this.wakeupFd[0];
            try (Arena arena = Arena.ofConfined()){
                MemorySegment buf = arena.allocate(128);
                MemoryLifetimeScope.of(arena).active(()->{
                    long ret = 0;
                    while ((ret = Unix.INSTANCE.read(fd,buf,buf.byteSize())) >0){}
                    if (ret==-1){
                        int errno = ErrorNo.getCapturedError().errno();
                        if (errno != Errno.EAGAIN){
                            logger.warn("Unexpected errno: {} on read wakeup fd: {}",errno,Unix.INSTANCE.strError(errno));
                        }
                    }
                });
            }

        }

        private void tryWakeup(int code){
            try (Arena arena = Arena.ofConfined()){
                MemorySegment buf = arena.allocate(ValueLayout.JAVA_INT);
                buf.set(ValueLayout.JAVA_INT,0,code);
                MemoryLifetimeScope.of(arena).active(()->{
                    long ret = Unix.INSTANCE.write(this.wakeupFd[1],buf,buf.byteSize());
                    if (ret<0){
                        int errno = ErrorNo.getCapturedError().errno();
                        logger.warn("Wakeup poller failed: {}",Unix.INSTANCE.strError(errno));
                    }
                });
            }
        }
        @Override
        public void wakeup(int code) {
            if (this.wakeup.compareAndSet(false,true)){
                tryWakeup(code);
            }
        }

        @Override
        public void close() {
            MemoryLifetimeScope.auto().active(()->{
                Unix.INSTANCE.close(wakeupFd[0]);
                Unix.INSTANCE.close(wakeupFd[1]);
            });

        }
    }

    class BusyPoller implements Poller{

        @Override
        public void poll() {
            try {
                Set<XskContext> receivedXsks = new HashSet<>();

                for (BpfRingBuffer ringBuffer : ringBuffers) {
                    ringBuffer.consume();
                }

                for (XskContext ctx : xskCtxs) {
                    ctx.releaseComp();
                    if (ctx.receive()>0){
                        receivedXsks.add(ctx);
                    }
                }
                processEgressQueue();
                for (XskContext ctx : receivedXsks) {
                    ctx.fillRing();
                }

                localBuffer.freeBlocks();
            } catch (Throwable e) {
                logger.error("Error occurred while xdp poll",e);
            }
        }

        @Override
        public void wakeup(int code) {

        }

        @Override
        public void close() {

        }
    }

}
