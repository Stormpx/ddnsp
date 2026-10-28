package io.crowds.proxy.transport.proxy.wisp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static io.crowds.proxy.transport.proxy.wisp.WispConstants.*;

/**
 * encodes {@link WispPacket} into the bytes of a websocket frame and decodes it back.
 */
@ChannelHandler.Sharable
public class WispCodec extends MessageToMessageCodec<ByteBuf, WispPacket> {
    private final static Logger logger = LoggerFactory.getLogger(WispCodec.class);

    public static final WispCodec INSTANCE = new WispCodec();

    //----------------------------------------------------------------------------------------- encode

    @Override
    protected void encode(ChannelHandlerContext ctx, WispPacket msg, List<Object> out) throws Exception {
        ByteBufAllocator alloc = ctx.alloc();
        switch (msg) {
            case WispPacket.Connect connect -> {
                byte[] hostname = connect.hostname().getBytes(StandardCharsets.UTF_8);
                ByteBuf buf = header(alloc, PACKET_CONNECT, connect.streamId(), 3 + hostname.length);
                buf.writeByte(connect.streamType());
                buf.writeShortLE(connect.port());
                buf.writeBytes(hostname);
                out.add(buf);
            }
            case WispPacket.Data data -> {
                if (!data.payload().isReadable()) {
                    data.release();
                    return;
                }
                ByteBuf header = header(alloc, PACKET_DATA, data.streamId(), 0);
                out.add(Unpooled.wrappedUnmodifiableBuffer(header, data.payload()));
            }
            case WispPacket.Continue cont -> {
                ByteBuf buf = header(alloc, PACKET_CONTINUE, cont.streamId(), 4);
                buf.writeIntLE(cont.bufferRemaining());
                out.add(buf);
            }
            case WispPacket.Close close -> {
                ByteBuf buf = header(alloc, PACKET_CLOSE, close.streamId(), 1);
                buf.writeByte(close.reason());
                out.add(buf);
            }
            case WispPacket.Info info -> {
                ByteBuf buf = header(alloc, PACKET_INFO, info.streamId(), 2);
                buf.writeByte(info.major());
                buf.writeByte(info.minor());
                ByteBuf metadata = alloc.buffer();
                try {
                    for (WispExtension extension : info.extensions()) {
                        metadata.clear();
                        encodeExtension(metadata, extension);
                        buf.writeByte(extension.id());
                        buf.writeIntLE(metadata.readableBytes());
                        buf.writeBytes(metadata);
                    }
                } finally {
                    metadata.release();
                }
                out.add(buf);
            }
        }
    }

    private static ByteBuf header(ByteBufAllocator alloc, int type, int streamId, int payloadLength) {
        ByteBuf buf = alloc.buffer(5 + payloadLength);
        buf.writeByte(type);
        buf.writeIntLE(streamId);
        return buf;
    }

    private static void encodeExtension(ByteBuf out, WispExtension extension) {
        switch (extension) {
            case WispExtension.Udp udp -> {
            }
            case WispExtension.StreamOpenConfirmation confirmation -> {
            }
            case WispExtension.Motd motd -> {
                String message = motd.message();
                if (message != null) {
                    out.writeBytes(message.getBytes(StandardCharsets.UTF_8));
                }
            }
            case WispExtension.PasswordAuth passwordAuth -> out.writeByte(passwordAuth.required() ? 1 : 0);
            case WispExtension.PasswordAuthCredentials credentials -> {
                byte[] username = toBytes(credentials.username());
                out.writeByte(username.length);
                out.writeBytes(username);
                out.writeBytes(toBytes(credentials.password()));
            }
            case WispExtension.KeyAuth keyAuth -> {
                out.writeByte(keyAuth.required() ? 1 : 0);
                out.writeByte(keyAuth.algorithms());
                out.writeBytes(keyAuth.challenge());
            }
            case WispExtension.KeyAuthCredentials credentials -> {
                byte[] username = toBytes(credentials.username());
                out.writeByte(username.length);
                out.writeBytes(username);
                out.writeByte(credentials.algorithm());
                out.writeBytes(credentials.publicKeyHash());
                out.writeBytes(credentials.signature());
            }
        }
    }

    private static byte[] toBytes(String str) {
        return str == null ? new byte[0] : str.getBytes(StandardCharsets.UTF_8);
    }

    //----------------------------------------------------------------------------------------- decode

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        if (msg.readableBytes() < 5) {
            logger.warn("wisp: received a packet which is too short, readableBytes={}", msg.readableBytes());
            return;
        }
        int type = msg.readUnsignedByte();
        int streamId = (int) msg.readUnsignedIntLE();
        switch (type) {
            case PACKET_CONNECT -> {
                if (msg.readableBytes() < 3) {
                    logger.warn("wisp: received a malformed CONNECT packet");
                    return;
                }
                int streamType = msg.readUnsignedByte();
                int port = msg.readUnsignedShortLE();
                String hostname = msg.readCharSequence(msg.readableBytes(), StandardCharsets.UTF_8).toString();
                out.add(new WispPacket.Connect(streamId, streamType, hostname, port));
            }
            case PACKET_DATA -> out.add(new WispPacket.Data(streamId, msg.readRetainedSlice(msg.readableBytes())));
            case PACKET_CONTINUE -> {
                int bufferRemaining = msg.readableBytes() >= 4 ? msg.readIntLE() : 1;
                out.add(new WispPacket.Continue(streamId, bufferRemaining));
            }
            case PACKET_CLOSE -> {
                int reason = msg.isReadable() ? msg.readUnsignedByte() : CLOSE_UNSPECIFIED;
                out.add(new WispPacket.Close(streamId, reason));
            }
            case PACKET_INFO -> {
                if (msg.readableBytes() < 2) {
                    logger.warn("wisp: received a malformed INFO packet");
                    return;
                }
                int major = msg.readUnsignedByte();
                int minor = msg.readUnsignedByte();
                out.add(new WispPacket.Info(streamId, major, minor, decodeExtensions(msg)));
            }
            default -> logger.warn("wisp: received an unknown packet type {}", type);
        }
    }

    private static List<WispExtension> decodeExtensions(ByteBuf msg) {
        List<WispExtension> extensions = new ArrayList<>();
        while (msg.readableBytes() >= 5) {
            int id = msg.readUnsignedByte();
            long length = msg.readUnsignedIntLE();
            int remaining = msg.readableBytes();
            int payloadLength = length > remaining ? remaining : (int) length;
            byte[] metadata = new byte[payloadLength];
            msg.readBytes(metadata);
            WispExtension extension = decodeExtension(id, metadata);
            if (extension != null) {
                extensions.add(extension);
            } else {
                logger.debug("wisp: ignoring the unknown extension {}", id);
            }
            if (length > remaining) {
                break;
            }
        }
        return extensions;
    }

    private static WispExtension decodeExtension(int id, byte[] metadata) {
        switch (id) {
            case EXTENSION_UDP -> {
                return new WispExtension.Udp();
            }
            case EXTENSION_STREAM_OPEN_CONFIRMATION -> {
                return new WispExtension.StreamOpenConfirmation();
            }
            case EXTENSION_MOTD -> {
                return new WispExtension.Motd(new String(metadata, StandardCharsets.UTF_8));
            }
            case EXTENSION_PASSWORD_AUTH -> {
                return new WispExtension.PasswordAuth(metadata.length > 0 && metadata[0] != 0);
            }
            case EXTENSION_KEY_AUTH -> {
                if (metadata.length < 2) {
                    return null;
                }
                boolean required = metadata[0] != 0;
                int algorithms = metadata[1] & 0xff;
                byte[] challenge = new byte[metadata.length - 2];
                System.arraycopy(metadata, 2, challenge, 0, challenge.length);
                return new WispExtension.KeyAuth(required, algorithms, challenge);
            }
            default -> {
                return null;
            }
        }
    }
}
