package io.crowds.compoments.xdp;

import io.crowds.compoments.xdp.event.EpollFdHandler;
import io.crowds.compoments.xdp.event.FdHandle;
import io.crowds.compoments.xdp.event.FdHandler;
import io.crowds.compoments.xdp.event.FdRegistration;
import io.crowds.lib.Errno;
import io.crowds.lib.unix.Poll;
import io.crowds.lib.unix.Unix;
import io.crowds.lib.xdp.*;
import io.crowds.lib.xdp.ffi.IfXdp;
import io.crowds.lib.xdp.ffi.XdpDesc;
import io.crowds.lib.xdp.ffi.XskTxMetadata;
import io.crowds.util.Native;
import io.netty.channel.IoEventLoop;
import io.netty.util.internal.shaded.org.jctools.queues.MpscUnboundedArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stormpx.net.buffer.ByteArray;
import top.dreamlike.panama.generator.proxy.ErrorNo;
import top.dreamlike.panama.generator.proxy.MemoryLifetimeScope;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

public class XdpPoller {

    private static final Logger logger = LoggerFactory.getLogger(XdpPoller.class);
    private static final int BATCH_SIZE = 128;
    private static final int REFILL_SIZE = 64;
    private static final double LOW_WATER_MARK_FACTOR = 0.25;
    private static final double HIGH_WATER_MARK_FACTOR = 0.75;

    private final UmemBufferPoll bufferPoll;
    private final List<XskContext> xskCtxs;
    private final List<BpfRingHandle> ringBuffers = new CopyOnWriteArrayList<>();

    private final LocalBuffer localBuffer = new LocalBuffer();
    private final AtomicBoolean freeBlocksScheduled = new AtomicBoolean(false);
    private final IoEventLoop eventLoop;
    private final FdHandler fdHandler;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final CompletableFuture<Void> startedFuture = new CompletableFuture<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final CompletableFuture<Void> closedFuture = new CompletableFuture<>();

    private final Queue<TxDesc> egressQueue = new MpscUnboundedArrayQueue<>(4096);
    private final EgressTask egressTask = new EgressTask();
    private final XdpIngressHandler ingressHandler;

    public XdpPoller(UmemBufferPoll bufferPoll, List<XdpSocket> sockets,IoEventLoop eventLoop, XdpIngressHandler ingressHandler) {
        this.bufferPoll = bufferPoll;
        this.eventLoop = eventLoop;
        this.fdHandler = new EpollFdHandler(eventLoop);
        this.ingressHandler = ingressHandler;
        this.xskCtxs = sockets.stream().map(XskContext::new).toList();

    }

    class LocalBuffer{
        private final int localBlocksReversed = 32;
        private final List<Block> localBlocks = new ArrayList<>();
        private int blockIndex = 0;
        private boolean starvation;

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
                logger.debug("Umem frames exhausted");
                starvation = true;
                return false;
            }
            this.localBlocks.add(block);
            starvation = false;
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
                block = this.localBlocks.get(i);
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
            starvation = false;
        }
    }

    class XskContext implements FdHandle {
        private final XdpSocket socket;
        private int lastFill = 0;
        private int lastReceived = 0;
        private boolean refill=false;
        private int interestEvents = Poll.POLLIN;
        private FdRegistration registration;

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
                assert addr!=-1;
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

        int transmit(){
            Queue<TxDesc> egressQueue = XdpPoller.this.egressQueue;
            if (egressQueue.isEmpty()){
                return 0;
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
                    return 0;
                }
                egressSize = Math.min(reverseFrames,egressSize);
                if (egressSize==0||txRing.reserve(egressSize,idxPtr)==0){
                    //socket tx ring dose not enough space
                    return 0;
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

                return nb;
            }

        }

        void register(){
            this.registration = XdpPoller.this.fdHandler.register(this);
            registration.submit(Poll.POLLIN);
        }

        @Override
        public int fd() {
            return socket.fd();
        }

        @Override
        public void doRead() {
            releaseComp();
            int nb = receive();
//            logger.info("receive: {}",nb);
        }

        @Override
        public void doWrite() {
            releaseComp();
            while (!egressQueue.isEmpty()){
                int nb = transmit();
                int event = Poll.POLLIN;
                if (nb == 0 && !egressQueue.isEmpty()){
                    if (!localBuffer.starvation){
                        event |= Poll.POLLOUT;
                    }
                }
                if (interestEvents != event){
                    registration.submit(event);
                    interestEvents = event;
                    if ((event & Poll.POLLOUT) != 0 ){
                        txFull.incrementAndGet();
                        break;
                    }else{
                        txFull.decrementAndGet();
                    }
                }
            }
        }

        @Override
        public void post() {
            scheduleFreeBlocks();
        }

        public void cancel(){
            registration.cancel();
        }
    }

    final AtomicInteger txFull = new AtomicInteger(0);

    class BpfRingHandle implements FdHandle{
        private final BpfRingBuffer ringBuffer;
        private FdRegistration registration;
        BpfRingHandle(BpfRingBuffer ringBuffer) {
            this.ringBuffer = ringBuffer;
        }

        void register(){
            registration = XdpPoller.this.fdHandler.register(this);
            registration.submit(Poll.POLLIN);
        }

        @Override
        public int fd() {
            return ringBuffer.fd();
        }

        @Override
        public void doRead() {
            ringBuffer.consume();
        }
        @Override
        public void doWrite() {}

        public void cancel(){
            registration.cancel();
        }

    }

    private class EgressTask implements Runnable{
        private static final long MIN_DELAY_NANOS = TimeUnit.MICROSECONDS.toNanos(500);
        private static final long MAX_DELAY_NANOS = TimeUnit.MILLISECONDS.toNanos(5);
        private static final int EWMA_ALPHA_NUMERATOR = 8;
        private static final int EWMA_ALPHA_DENOMINATOR = 10;

        private static final AtomicLongFieldUpdater<EgressTask> LAST_TX_UPDATER = AtomicLongFieldUpdater.newUpdater(EgressTask.class,"lastTxNanoTime");
        private static final AtomicLongFieldUpdater<EgressTask> EWMA_UPDATER = AtomicLongFieldUpdater.newUpdater(EgressTask.class,"ewmaIntervalNanos");
        private volatile long lastTxNanoTime = 0;
        private volatile long ewmaIntervalNanos = TimeUnit.MILLISECONDS.toNanos(1);
        private volatile io.netty.util.concurrent.Future<?> timeout;

        private boolean isScheduled(){
            return timeout!=null;
        }

        private void cancel(){
            if (timeout!=null){
                timeout.cancel(false);
                timeout = null;
            }
        }

        private void schedule(){
            timeout = eventLoop.schedule(this,computeAdaptiveDelay(),TimeUnit.NANOSECONDS);
        }

        private long computeAdaptiveDelay(){
            long interval = ewmaIntervalNanos;
            return Math.clamp(interval * 2, MIN_DELAY_NANOS, MAX_DELAY_NANOS);
        }

        private void updateInterval(){
            long now = System.nanoTime();
            while (true) {
                long last = lastTxNanoTime;
                if (now < last){
                    break;
                }
                if (LAST_TX_UPDATER.compareAndSet(this,last,now)){
                    EWMA_UPDATER.updateAndGet(this,ewma->{
                        long interval = now - last;
                        return (ewma * (EWMA_ALPHA_DENOMINATOR - EWMA_ALPHA_NUMERATOR) + interval * EWMA_ALPHA_NUMERATOR) / EWMA_ALPHA_DENOMINATOR;
                    });
                    break;
                }
            }
        }

        @Override
        public void run() {
            if (XdpPoller.this.closed.get()){
                return;
            }

            flushEgressQueue();

            if (!egressQueue.isEmpty()){
                schedule();
            }else{
                timeout = null;
            }
        }
    }


    private void scheduleFreeBlocks() {
        if (freeBlocksScheduled.compareAndSet(false, true)) {
            eventLoop.execute(() -> {
                try {
                    if (localBuffer.starvation){
                        for (XskContext xskCtx : xskCtxs) {
                            xskCtx.releaseComp();
                        }
                        egressTask.schedule();
                    }
                    for (XskContext xskCtx : xskCtxs) {
                        xskCtx.fillRing();
                    }
                    localBuffer.freeBlocks();
                }finally {
                    freeBlocksScheduled.set(false);
                }
            });
        }
    }


    private void flushEgressQueue(){
        if (egressQueue.isEmpty()){
            return;
        }
        for (XskContext xskCtx : xskCtxs) {
            if ((xskCtx.interestEvents & Poll.POLLOUT) == 0){
                xskCtx.doWrite();
            }
            if (egressQueue.isEmpty()||localBuffer.starvation){
                break;
            }
        }
    }

    public CompletableFuture<Void> start(){
        if (this.closed.get()){
            throw new IllegalStateException("Poller already closed");
        }
        if (started.compareAndSet(false,true)) {
            eventLoop.execute(() -> {
                for (XskContext ctx : this.xskCtxs) {
                    ctx.fillRing();
                    ctx.register();
                }
                for (BpfRingHandle ringBuffer : this.ringBuffers) {
                    ringBuffer.register();
                }
                startedFuture.complete(null);
            });
        }
        return startedFuture;
    }

    public void addRingBuffer(BpfRingBuffer ringBuffer){
        BpfRingHandle handle = new BpfRingHandle(ringBuffer);
        this.ringBuffers.add(handle);
        if (started.get()){
            handle.register();
        }
    }

    final AtomicBoolean flush = new AtomicBoolean(false);
    public void transmitData(TxDesc data){
        if (this.egressQueue.add(data)){
            // Update EWMA of inter-packet arrival interval
            egressTask.updateInterval();

            if (egressQueue.size() > 16 && txFull.get() < xskCtxs.size() && flush.compareAndSet(false,true)){
//                logger.info("doFlush");
                eventLoop.execute(()->{
                    egressTask.cancel();
                    flushEgressQueue();
                    flush.set(false);
                });
            } else if (!egressTask.isScheduled()) {
                egressTask.schedule();
            }
        }
    }

    public CompletableFuture<Void> stop(){
        if (!this.started.get()){
            throw new IllegalStateException("Poller not running");
        }
        if (this.closed.compareAndSet(false,true)){
            eventLoop.execute(()->{
                egressTask.cancel();
                for (XskContext ctx : this.xskCtxs) {
                    ctx.cancel();
                }
                for (BpfRingHandle ringBuffer : this.ringBuffers) {
                    ringBuffer.cancel();
                }

            });
        }
        return this.closedFuture;
    }

//    interface Poller{
//
//        void poll();
//
//        void wakeup(int code);
//
//        void close();
//    }
//
//    class SysPoller implements Poller{
//
//        private final int[] wakeupFd;
//        private final AtomicBoolean wakeup = new AtomicBoolean(false);
//
//        public SysPoller() {
//            this.wakeupFd = Unix.INSTANCE.pipe(Unix.O_DIRECT|Unix.O_NONBLOCK);
//            eventHandler.put(this.wakeupFd[0],this::drainWakeupMessage);
//        }
//
//        private NativeArray<PollFd> buildPollFds(Arena arena){
//            NativeArray<PollFd> pollFds = Native.structArrayAlloc(arena, PollFd.class, 1 + xskCtxs.size()+ringBuffers.size());
//            int index = 1;
//            for (BpfRingBuffer ringBuffer : ringBuffers) {
//                PollFd pollFd = pollFds.get(index++);
//                pollFd.setFd(ringBuffer.fd());
//                pollFd.setEvents((short) Poll.POLLIN);
//            }
//            for (XskContext xskCtx : xskCtxs) {
//                PollFd pollFd = pollFds.get(index++);
//                pollFd.setFd(xskCtx.socket.fd());
//                pollFd.setEvents((short) xskCtx.interestEvents);
//            }
//            PollFd pollFd = pollFds.getFirst();
//            pollFd.setFd(this.wakeupFd[0]);
//            pollFd.setEvents((short) Poll.POLLIN);
//
//            return pollFds;
//        }
//
//        @Override
//        public void poll() {
//            try (Arena arena = Arena.ofConfined()){
//                var pollFds = buildPollFds(arena);
//                int poll = MemoryLifetimeScope.of(arena).active(()-> Poll.INSTANCE.poll(pollFds, pollFds.size(), 100));
//                if (poll<0){
//                    logger.error("Poll return Error: {}",Unix.INSTANCE.strError(ErrorNo.getCapturedError().errno()));
//                    return;
//                }
//                if (poll==0){
//                    return;
//                }
//                for (XskContext ctx : xskCtxs) {
//                    ctx.releaseComp();
//                }
//                boolean outEvent = false;
//                Set<Integer> readableFds = new HashSet<>();
//                for (PollFd pollFd : pollFds) {
//                    int fd = pollFd.getFd();
//                    short revents = pollFd.getRevents();
//
//                    if ((revents&Poll.POLLOUT)!=0){
//                        outEvent = true;
//                    }
//                    if ((revents&Poll.POLLIN)!=0){
//                        try {
//                            Runnable runnable = eventHandler.get(fd);
//                            if (runnable!=null){
//                                readableFds.add(fd);
//                                runnable.run();
//                            }
//                        } catch (Exception e) {
//                            logger.error("Error occurred while processing the read event fd: {}", fd,e);
//                        }
//                    }
//                }
//
//                if (outEvent||this.wakeup.get()){
//                    interestEvents &= ~Poll.POLLOUT;
//                    TxResult result = processEgressQueue();
//                    this.wakeup.set(false);
//                    if (!result.oom){
//                        processEgressQueue();
//                    }
//                    if (!egressQueue.isEmpty()){
//                        interestEvents |= Poll.POLLOUT;
//                    }
//                }
//                for (XskContext xskCtx : xskCtxs) {
//                    if (readableFds.contains(xskCtx.socket.fd())) {
//                        xskCtx.fillRing();
//                    }
//                }
//                localBuffer.freeBlocks();
//            } catch (Throwable e) {
//                logger.error("Error occurred while xdp poll",e);
//            }
//        }
//
//
//        private void drainWakeupMessage(){
//            int fd = this.wakeupFd[0];
//            try (Arena arena = Arena.ofConfined()){
//                MemorySegment buf = arena.allocate(128);
//                MemoryLifetimeScope.of(arena).active(()->{
//                    long ret = 0;
//                    while ((ret = Unix.INSTANCE.read(fd,buf,buf.byteSize())) >0){}
//                    if (ret==-1){
//                        int errno = ErrorNo.getCapturedError().errno();
//                        if (errno != Errno.EAGAIN){
//                            logger.warn("Unexpected errno: {} on read wakeup fd: {}",errno,Unix.INSTANCE.strError(errno));
//                        }
//                    }
//                });
//            }
//
//        }
//
//        private void tryWakeup(int code){
//            try (Arena arena = Arena.ofConfined()){
//                MemorySegment buf = arena.allocate(ValueLayout.JAVA_INT);
//                buf.set(ValueLayout.JAVA_INT,0,code);
//                MemoryLifetimeScope.of(arena).active(()->{
//                    long ret = Unix.INSTANCE.write(this.wakeupFd[1],buf,buf.byteSize());
//                    if (ret<0){
//                        int errno = ErrorNo.getCapturedError().errno();
//                        logger.warn("Wakeup poller failed: {}",Unix.INSTANCE.strError(errno));
//                    }
//                });
//            }
//        }
//        @Override
//        public void wakeup(int code) {
//            if (this.wakeup.compareAndSet(false,true)){
//                tryWakeup(code);
//            }
//        }
//
//        @Override
//        public void close() {
//            MemoryLifetimeScope.auto().active(()->{
//                Unix.INSTANCE.close(wakeupFd[0]);
//                Unix.INSTANCE.close(wakeupFd[1]);
//            });
//
//        }
//    }


}
