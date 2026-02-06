package io.crowds.proxy.transport.proxy.tuic;

import io.crowds.compoments.tls.TlsUtils;
import io.crowds.proxy.NetAddr;
import io.crowds.proxy.transport.proxy.tuic.udp.UdpSessions;
import io.crowds.util.Async;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.quic.*;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class TuicConnection {

    private static final Logger logger = LoggerFactory.getLogger(TuicConnection.class);
    private final Channel channel;
    private final User user;
    private final NetAddr serverAddr;
    private final UdpMode udpMode;

    private final AtomicReference<State> state = new AtomicReference<>(State.ACTIVE);

    private final Promise<Void> closePromise;

    private String[] alpn;
    private boolean tlsInsecure = false;
    private int maxDatagramSize = 1200;

    private QuicChannel quicChannel;
    private volatile Future<QuicChannel> quicConnectFuture;
    private final PacketCommandHandler packetCommandHandler = new PacketCommandHandler();
    private final UdpSessions udpSessions;

    private ScheduledFuture<?> scheduledHeartbeat;
    private long connectCount;

    public TuicConnection(Channel channel, User user, NetAddr serverAddr, UdpMode udpMode) {
        this.channel = channel;
        this.user = user;
        this.serverAddr = serverAddr;
        this.udpMode = udpMode;
        this.udpSessions = new UdpSessions(this);
        this.closePromise = channel.eventLoop().newPromise();
    }

    enum State{
        ACTIVE,
        SHUTDOWN,
        CLOSED
    }

    private void ensureActive(){
        State state = this.state.get();
        if (state == State.SHUTDOWN){
            throw new IllegalStateException("Connection is shutting down");
        }
        if (state== State.CLOSED){
            throw new IllegalStateException("Connection is closed");
        }
    }

    private void doAuthenticate() {
        quicChannel.createStream(QuicStreamType.UNIDIRECTIONAL,new TuicCodec())
                .addListener(f->{
                    if (!f.isSuccess()) {
                        logger.error("Unable open unidirectional stream to send auth command",f.cause());
                        return;
                    }
                    QuicStreamChannel quicStreamChannel = (QuicStreamChannel) f.get();
                    UUID uuid = user.uuid();
                    String password = user.password();
                    byte[] label= ByteBufUtil.getBytes(Unpooled.buffer(16).writeLong(uuid.getMostSignificantBits()).writeLong(uuid.getLeastSignificantBits()));
                    byte[] token = TlsUtils.exportKeyingMaterial(quicChannel,label,password.getBytes(),32);
                    quicStreamChannel.writeAndFlush(new TuicCommand.Auth(uuid,token)).addListener(ChannelFutureListener.CLOSE);
                });
    }

    private void scheduleHeartbeat(int interval) {
        if (this.scheduledHeartbeat != null) {
            this.scheduledHeartbeat.cancel(false);
            this.scheduledHeartbeat = null;
        }
        this.scheduledHeartbeat = quicChannel.eventLoop().scheduleAtFixedRate(()->{
            if (!quicChannel.isActive()) {
                return;
            }
            if (connectCount + udpSessions.getAssociateCount() == 0){
                return;
            }

            quicChannel.writeAndFlush(new TuicCommand.Heartbeat());

        },interval,interval,TimeUnit.SECONDS);

    }

    private InetSocketAddress setupNatHandler(){
        final var stubAddr = new InetSocketAddress(NetUtil.createInetAddressFromIpAddressString("255.255.255.255"), serverAddr.getPort());
        channel.pipeline()
                .addLast(new ChannelDuplexHandler(){
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (msg instanceof DatagramPacket packet) {
                            msg = new DatagramPacket(packet.content(),packet.recipient()==null? (InetSocketAddress) ctx.channel().localAddress() :packet.recipient(),stubAddr);
                        }
                        super.channelRead(ctx, msg);
                    }

                    @Override
                    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                        if (msg instanceof DatagramPacket packet){
                            msg = new DatagramPacket(packet.content(),serverAddr.getAsInetAddr(),packet.sender());
                        }
                        super.write(ctx, msg, promise);
                    }
                });
        return stubAddr;
    }

    private Future<QuicChannel> quicConnect(){
        InetSocketAddress remoteAddress = serverAddr.getAsInetAddr();
        if (remoteAddress.isUnresolved()){
            remoteAddress = setupNatHandler();
        }
        QuicSslContextBuilder builder = QuicSslContextBuilder.forClient();
        if (alpn!=null){
            builder.applicationProtocols(alpn);
        }
        if (tlsInsecure) {
            builder.trustManager(InsecureTrustManagerFactory.INSTANCE);
        }
        QuicSslContext context = builder.build();
        ChannelHandler codec = new QuicClientCodecBuilder()
                .sslContext(context)
                .datagram(2048,2048)
                .maxIdleTimeout(30 * 1000, TimeUnit.MILLISECONDS)
                .initialMaxData(Integer.MAX_VALUE)
                .initialMaxStreamDataBidirectionalLocal(Integer.MAX_VALUE)
                .initialMaxStreamDataBidirectionalRemote(Integer.MAX_VALUE)
                .initialMaxStreamDataUnidirectional(Integer.MAX_VALUE)
                .initialMaxStreamsBidirectional(Integer.MAX_VALUE)
                .initialMaxStreamsUnidirectional(Integer.MAX_VALUE)
                .maxSendUdpPayloadSize(maxDatagramSize)
                .maxRecvUdpPayloadSize(65535)
                .build();

        channel.pipeline().addLast("quicCodec",codec);

        QuicChannelBootstrap bootstrap = QuicChannel.newBootstrap(channel);
        Future<QuicChannel> future = bootstrap.remoteAddress(remoteAddress)
                .handler(new ChannelInitializer<QuicChannel>() {
                    @Override
                    protected void initChannel(QuicChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new TuicCodec())
                                .addLast(packetCommandHandler)
                                .addLast(new ChannelInboundHandlerAdapter(){
                                    @Override
                                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                        if (evt instanceof QuicConnectionCloseEvent closeEvent){
                                            logger.info("Quic connection close by peer: {}", new String(closeEvent.reason()));
                                            doClose();
                                        }
                                        super.userEventTriggered(ctx, evt);
                                    }
                                });
                    }
                })
                .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) throws Exception {
                        if (ch.type() != QuicStreamType.UNIDIRECTIONAL){
                            ch.close();
                            return;
                        }
                        ch.pipeline()
                                .addLast(new TuicCodec())
                                .addLast(packetCommandHandler);
                    }
                })
                .connect();
        future.addListener(f->{
            if (!f.isSuccess()) {
                doClose();
                return;
            }
            QuicChannel quicChannel = (QuicChannel) f.get();
            quicChannel.closeFuture().addListener(_-> doClose());
            if (this.state.get() != State.ACTIVE) {
                quicChannel.close();
                return;
            }

            this.quicChannel = quicChannel;
            doAuthenticate();
            scheduleHeartbeat(3);

        });
        return future;
    }

    private void doClose(){
        EventLoop eventLoop = eventLoop();
        if (eventLoop.inEventLoop()) {
            if (!this.state.compareAndSet(State.ACTIVE,State.SHUTDOWN)){
                return;
            }
            try {
                ScheduledFuture<?> scheduledHeartbeat = this.scheduledHeartbeat;
                if (scheduledHeartbeat!=null && scheduledHeartbeat.isCancellable()){
                    scheduledHeartbeat.cancel(false);
                }
                this.udpSessions.close();
                QuicChannel quicChannel = this.quicChannel;
                if (quicChannel!=null){
                    quicChannel.close().addListener(_->{
                        try {
                            this.channel.close();
                            this.state.set(State.CLOSED);
                            this.closePromise.trySuccess(null);
                        } catch (Exception e) {
                            logger.error("",e);
                        }
                    });
                }else{
                    this.channel.close();
                    this.state.set(State.CLOSED);
                    this.closePromise.trySuccess(null);
                }
            } catch (Exception e) {
                logger.error("",e);
            }
        }else{
            eventLoop.execute(this::doClose);
        }

    }

    private Future<QuicChannel> getQuicChannel(){
        EventLoop eventLoop = eventLoop();
        if (quicChannel != null){
            return eventLoop.newSucceededFuture(quicChannel);
        }
        if (quicConnectFuture!=null){
            return quicConnectFuture;
        }
        Promise<QuicChannel> promise = eventLoop.newPromise();
        eventLoop.execute(()->{
            Future<QuicChannel> quicConnect = quicConnectFuture;
            if (quicConnect==null){
                quicConnect = quicConnect();
                this.quicConnectFuture = quicConnect;
            }
            quicConnect.addListener(Async.cascade(promise));
        });

        return promise;
    }

    public EventLoop eventLoop() {
        return channel.eventLoop();
    }

    public boolean isActive(){
        return state.get() == State.ACTIVE;
    }
    public boolean isClosed(){
        return state.get() == State.CLOSED;
    }


    public UdpMode getUdpMode() {
        return udpMode;
    }

    public int maxDatagramSize(){
        return maxDatagramSize;
    }

    public TuicConnection setAlpn(String... alpn) {
        this.alpn = alpn;
        return this;
    }

    public TuicConnection setTlsInsecure(boolean tlsInsecure) {
        this.tlsInsecure = tlsInsecure;
        return this;
    }

    public Future<Channel> connect(NetAddr netAddr){
        ensureActive();
        EventLoop eventLoop = eventLoop();
        Promise<Channel> promise = eventLoop.newPromise();

        Async.cascadeFailure(getQuicChannel(),promise,f->{
            QuicChannel quicChannel = f.get();
            var quicStreamFuture = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, new ChannelInitializer<QuicStreamChannel>() {
                @Override
                protected void initChannel(QuicStreamChannel ch) throws Exception {
                    ConnectHandler connectHandler = new ConnectHandler(netAddr);
                    ch.pipeline()
                            .addLast(new TuicCodec())
                            .addLast(connectHandler)
                            ;

                    connectCount++;
                    ch.closeFuture().addListener(_->connectCount--);

                    eventLoop.schedule(()->{
                        if (!connectHandler.commandWritten && ch.isActive()) {
                            ch.writeAndFlush(Unpooled.EMPTY_BUFFER);
                        }
                    },50,TimeUnit.MILLISECONDS);
                }
            });
            Async.cascadeFailure(quicStreamFuture,promise,qsf-> promise.trySuccess(qsf.getNow()));
        });

        return promise;
    }

    public void sendUdpCommand(TuicCommand command){

        if (!Tuic.isUdpCommand(command)){
            return;
        }

        getQuicChannel().addListener(quicFuture->{
            if (!quicFuture.isSuccess()) {
                logger.error("Failed to send packet to tuic endpoint: {}",quicFuture.cause().getMessage(),quicFuture.cause());
                return;
            }
            QuicChannel quicChannel = (QuicChannel) quicFuture.get();
            if (!quicChannel.isActive()) {
                if (command instanceof TuicCommand.Packet packet){
                    ReferenceCountUtil.safeRelease(packet.data());
                }
                return;
            }
            switch (udpMode){
                case NATIVE -> {
                    if (command instanceof TuicCommand.Packet packet){
                        try {
                            int associateId = packet.assocId();
                            int pktId = packet.pktId();
                            NetAddr addr = packet.addr();
                            ByteBuf data = packet.data();
                            int maxPktSize = maxDatagramSize();
                            int firstFragSize = maxPktSize - Tuic.addrLength(addr);
                            int otherFragSize = maxPktSize - Tuic.addrLength(null);

                            int fragTotal = 1;
                            if (firstFragSize < data.readableBytes()){
                                fragTotal += ((data.readableBytes() - firstFragSize) / otherFragSize);
                            }

                            int fragId = 0;
                            while (data.isReadable()){
                                if (fragId==0) {
                                    ByteBuf frag = data.readRetainedSlice(Math.min(firstFragSize, data.readableBytes()));
                                    quicChannel.write(new TuicCommand.Packet(associateId,pktId, (short) fragTotal,(short) fragId++,addr,frag));
                                }else{
                                    ByteBuf frag = data.readRetainedSlice(Math.min(otherFragSize, data.readableBytes()));
                                    quicChannel.write(new TuicCommand.Packet(associateId,pktId, (short) fragTotal,(short) fragId++,null,frag));
                                }
                            }
                            quicChannel.flush();
                        } finally {
                            ReferenceCountUtil.release(packet);
                        }
                    }else{
                        quicChannel.writeAndFlush(command);
                    }
                }
                case QUIC -> {
                    quicChannel.createStream(QuicStreamType.UNIDIRECTIONAL, new ChannelInitializer<QuicStreamChannel>() {
                        @Override
                        protected void initChannel(QuicStreamChannel ch) throws Exception {
                            ch.pipeline().addLast(new TuicCodec());
                        }
                    }).addListener(f->{
                        QuicStreamChannel channel = (QuicStreamChannel) f.get();
                        channel.writeAndFlush(command).addListener(ChannelFutureListener.CLOSE);
                    });
                }
            }

        });
    }


    public Future<Channel> associate(){
        ensureActive();
        return udpSessions.associate();
    }


    public Future<Void> close(){
        doClose();
        return closePromise;
    }

    public Future<Void> closeFuture(){
        return closePromise;
    }



    private final class ConnectHandler extends ChannelOutboundHandlerAdapter{

        private final NetAddr target;
        private boolean commandWritten;

        private ConnectHandler(NetAddr target) {
            this.target = target;
            this.commandWritten = false;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (!commandWritten){
                ctx.write(new TuicCommand.Connect(target));
                commandWritten = true;
            }
            super.write(ctx, msg, promise);
        }


    }

    @ChannelHandler.Sharable
    private final class PacketCommandHandler extends ChannelInboundHandlerAdapter{

        private void unhandled(ChannelHandlerContext ctx, Object msg){
            ReferenceCountUtil.safeRelease(msg);
            if (ctx.channel() instanceof QuicStreamChannel){
                ctx.close();
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!(msg instanceof TuicCommand command)) {
                super.channelRead(ctx, msg);
                return;
            }
            if (command instanceof TuicCommand.Heartbeat){
                return;
            }
            if (!(command instanceof TuicCommand.Packet packet)) {
                logger.warn("Unhandled Command received {}", command);
                unhandled(ctx, msg);
                return;
            }

            TuicConnection.this.udpSessions.recvPacket(packet);

        }

    }
}
