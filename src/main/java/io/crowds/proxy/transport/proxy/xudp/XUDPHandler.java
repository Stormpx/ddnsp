package io.crowds.proxy.transport.proxy.xudp;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.TP;
import io.crowds.util.Hash;
import io.crowds.util.Rands;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.DecoderException;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

import static io.crowds.proxy.transport.proxy.xudp.Mux.*;

/**
 * One XUDP session: a connection maps to a single GlobalID and carries UDP datagrams only.
 * The peer (server) only sends Keep / End, this side only sends New / Keep.
 */
public class XUDPHandler extends ChannelDuplexHandler {
    private static final Logger logger = LoggerFactory.getLogger(XUDPHandler.class);

    public static final NetAddr ADDRESS = NetAddr.of("v1.mux.cool",666);
    private static final byte[] BASE_KEY = Rands.genBytes(32);

    private final byte[] globalId;
    private boolean newFrameWritten;

    public XUDPHandler(byte[] globalId) {
        this.globalId = globalId;
    }

    public static byte[] getGlobalId(NetAddr src){
        return Hash.kdfBlake3(src.toString().getBytes(StandardCharsets.US_ASCII),BASE_KEY,GLOBAL_ID_LEN);
    }

    private static String payloadText(ByteBuf data){
        if (data==null || !data.isReadable()){
            return "";
        }
        if (data.readableBytes() > 256){
            return "(" + data.readableBytes() + " bytes)";
        }
        return data.toString(CharsetUtil.UTF_8);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof Mux mux)){
            super.channelRead(ctx, msg);
            return;
        }
        try {
            switch (mux){
                case Mux.Keep keep -> {
                    if (keep.data()==null || !keep.data().isReadable()){
                        //the protocol allows a Keep without payload, but it is useless here (no datagram to deliver)
                        logger.debug("drop payload-less keep frame");
                        return;
                    }
                    //note: this implementation only handles the UDP direction, so replies from the server must
                    //carry network=UDP with a target override (Xray behavior), otherwise there is no way to tell
                    //which target a datagram came from
                    if (keep.network() != TP.UDP || keep.addr() == null){
                        throw new DecoderException("[xudp] unsupported keep frame: expect network=UDP with target, but network="
                                + keep.network() + ", target=" + keep.addr());
                    }
                    ctx.fireChannelRead(new DatagramPacket(keep.data().retain(), null, keep.addr().getAsInetAddr()));
                }
                case Mux.End end -> {
                    if (end.error()){
                        logger.warn("xudp server closed the stream with error, payload: {}", payloadText(end.data()));
                    }else{
                        logger.debug("xudp server closed the stream, payload: {}", payloadText(end.data()));
                    }
                    ctx.close();
                }
                case Mux.KeepAlive keepAlive -> logger.debug("ignore xudp keepalive frame");
                case Mux.New aNew -> throw new DecoderException("[xudp] server must not send New frame: network="
                        + aNew.network() + ", target=" + aNew.addr() + ", globalId=" + describe(aNew.globalId()));
            }
        } finally {
            ReferenceCountUtil.safeRelease(mux);
        }
    }

    private static String describe(byte[] bytes){
        if (bytes==null){
            return "null";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes){
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof DatagramPacket packet){
            if (!newFrameWritten){
                //first frame: New + target + GlobalID + first datagram (must carry a payload, otherwise the
                //server keeps waiting for the length field)
                ctx.write(new Mux.New(0, TP.UDP,NetAddr.of(packet.recipient()),globalId,packet.content()),promise);
                newFrameWritten = true;
            }else{
                //following frames: Keep + target override (this is what tells the targets of one connection apart)
                ctx.write(new Mux.Keep(0,TP.UDP,NetAddr.of(packet.recipient()),packet.content()),promise);
            }
            return;
        }
        if (msg instanceof ByteBuf buf){
            //a bare buffer must not be written into the mux stream: it would flush all following frames
            logger.error("drop plain buffer write in xudp pipeline: {} bytes", buf.readableBytes(), new IllegalStateException("unexpected write"));
            ReferenceCountUtil.safeRelease(buf);
            promise.tryFailure(new DecoderException("[xudp] unexpected buffer write"));
            return;
        }
        super.write(ctx, msg, promise);
    }
}
