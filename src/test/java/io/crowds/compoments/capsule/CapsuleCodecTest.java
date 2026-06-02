package io.crowds.compoments.capsule;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.TooLongFrameException;
import org.junit.Assert;
import org.junit.Test;

public class CapsuleCodecTest {

    // ---- helper: manually encode a varint for decoder tests ----

    private static void writeVarInt(long val, ByteBuf out) {
        if (val < 63) {
            out.writeByte((int) val);
        } else if (val < 16383) {
            out.writeShort((int) (0x4000 + val));
        } else if (val < 1073741823L) {
            out.writeInt((int) (0x80000000L + val));
        } else if (val < 4611686018427387903L) {
            out.writeLong(0xc000000000000000L + val);
        } else {
            throw new IllegalArgumentException("Variable-Length Integer too large");
        }
    }

    private static ByteBuf buildCapsuleBytes(int type, byte[] data) {
        ByteBuf buf = Unpooled.buffer();
        writeVarInt(type, buf);
        writeVarInt(data.length, buf);
        buf.writeBytes(data);
        return buf;
    }

    // ==================== Decoder tests ====================

    @Test
    public void testDecodeSingleCapsule1ByteVarInt() {
        EmbeddedChannel ch = new EmbeddedChannel(new CapsuleDecoder(1024));
        byte[] payload = {1, 2, 3, 4, 5};
        ByteBuf in = buildCapsuleBytes(0, payload);
        Assert.assertTrue(ch.writeInbound(in));

        Capsule capsule = ch.readInbound();
        Assert.assertNotNull(capsule);
        Assert.assertEquals(0, capsule.type());
        Assert.assertEquals(5, capsule.content().readableBytes());
        byte[] decoded = new byte[5];
        capsule.content().readBytes(decoded);
        Assert.assertArrayEquals(payload, decoded);
        capsule.release();
        Assert.assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    public void testDecodeSingleCapsule2ByteVarInt() {
        EmbeddedChannel ch = new EmbeddedChannel(new CapsuleDecoder(65536));
        byte[] payload = new byte[100];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) i;

        ByteBuf in = buildCapsuleBytes(100, payload);
        Assert.assertTrue(ch.writeInbound(in));

        Capsule capsule = ch.readInbound();
        Assert.assertNotNull(capsule);
        Assert.assertEquals(100, capsule.type());
        Assert.assertEquals(100, capsule.content().readableBytes());
        byte[] decoded = new byte[100];
        capsule.content().readBytes(decoded);
        Assert.assertArrayEquals(payload, decoded);
        capsule.release();
        Assert.assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    public void testDecodeSingleCapsule4ByteVarInt() {
        EmbeddedChannel ch = new EmbeddedChannel(new CapsuleDecoder(1 << 20));
        int type = 20000;
        byte[] payload = new byte[20000];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i & 0xff);

        ByteBuf in = buildCapsuleBytes(type, payload);
        Assert.assertTrue(ch.writeInbound(in));

        Capsule capsule = ch.readInbound();
        Assert.assertNotNull(capsule);
        Assert.assertEquals(type, capsule.type());
        Assert.assertEquals(20000, capsule.content().readableBytes());
        Assert.assertEquals(0, capsule.content().getByte(0));
        Assert.assertEquals((byte) (255 & 0xff), capsule.content().getByte(255));
        capsule.release();
        Assert.assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    public void testDecodeEmptyPayload() {
        EmbeddedChannel ch = new EmbeddedChannel(new CapsuleDecoder(1024));
        ByteBuf in = buildCapsuleBytes(5, new byte[0]);
        Assert.assertTrue(ch.writeInbound(in));

        Capsule capsule = ch.readInbound();
        Assert.assertNotNull(capsule);
        Assert.assertEquals(5, capsule.type());
        Assert.assertEquals(0, capsule.content().readableBytes());
        capsule.release();
        Assert.assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    public void testDecodeMultipleCapsules() {
        EmbeddedChannel ch = new EmbeddedChannel(new CapsuleDecoder(65536));

        ByteBuf buf = Unpooled.buffer();
        byte[] data1 = "hello".getBytes();
        writeVarInt(1, buf);
        writeVarInt(data1.length, buf);
        buf.writeBytes(data1);
        byte[] data2 = "world!!".getBytes();
        writeVarInt(2, buf);
        writeVarInt(data2.length, buf);
        buf.writeBytes(data2);

        Assert.assertTrue(ch.writeInbound(buf));

        Capsule c1 = ch.readInbound();
        Assert.assertNotNull(c1);
        Assert.assertEquals(1, c1.type());
        byte[] d1 = new byte[c1.content().readableBytes()];
        c1.content().readBytes(d1);
        Assert.assertArrayEquals(data1, d1);
        c1.release();

        Capsule c2 = ch.readInbound();
        Assert.assertNotNull(c2);
        Assert.assertEquals(2, c2.type());
        byte[] d2 = new byte[c2.content().readableBytes()];
        c2.content().readBytes(d2);
        Assert.assertArrayEquals(data2, d2);
        c2.release();

        Assert.assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    public void testDecodeFragmentedDelivery() {
        EmbeddedChannel ch = new EmbeddedChannel(new CapsuleDecoder(1024));
        byte[] payload = {10, 20, 30};
        ByteBuf full = buildCapsuleBytes(7, payload);

        while (full.isReadable()) {
            ByteBuf slice = full.readSlice(1).retain();
            ch.writeInbound(slice);
        }
        full.release();

        Capsule capsule = ch.readInbound();
        Assert.assertNotNull(capsule);
        Assert.assertEquals(7, capsule.type());
        byte[] decoded = new byte[3];
        capsule.content().readBytes(decoded);
        Assert.assertArrayEquals(payload, decoded);
        capsule.release();
        Assert.assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    public void testDecodeExceedsMaximumLength() {
        long maxLength = 10;
        EmbeddedChannel ch = new EmbeddedChannel(new CapsuleDecoder(maxLength));
        byte[] payload = new byte[20];
        ByteBuf in = buildCapsuleBytes(1, payload);
        ch.writeInbound(in);
        ch.finish();
    }

    @Test(expected = TooLongFrameException.class)
    public void testDecodeLengthExceedsIntegerMax() {
        EmbeddedChannel ch = new EmbeddedChannel(new CapsuleDecoder(1024));
        ByteBuf in = Unpooled.buffer();
        writeVarInt(1, in);
        in.writeLong(0xc000000080000000L);
        in.writeBytes(new byte[1]);

        try {
            ch.writeInbound(in);
        } finally {
            ch.finish();
        }
    }

    // ==================== Encoder tests ====================

    @Test
    public void testEncodeSingleCapsule1ByteVarInt() {
        EmbeddedChannel ch = new EmbeddedChannel(new CapsuleEncoder());
        byte[] data = {1, 2, 3};
        ByteBuf content = Unpooled.wrappedBuffer(data);
        Assert.assertTrue(ch.writeOutbound(new Capsule(0, content)));

        ByteBuf out = ch.readOutbound();
        Assert.assertNotNull(out);
        // type=0 -> 1-byte varint: 0x00
        Assert.assertEquals(0, out.readUnsignedByte());
        // length=3 -> 1-byte varint: 0x03
        Assert.assertEquals(3, out.readUnsignedByte());
        // data
        Assert.assertEquals(1, out.readByte());
        Assert.assertEquals(2, out.readByte());
        Assert.assertEquals(3, out.readByte());
        Assert.assertFalse(out.isReadable());
        out.release();
        Assert.assertNull(ch.readOutbound());
        ch.finish();
    }

    @Test
    public void testEncodeSingleCapsule2ByteVarInt() {
        EmbeddedChannel ch = new EmbeddedChannel(new CapsuleEncoder());
        byte[] data = new byte[100];
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;
        ByteBuf content = Unpooled.wrappedBuffer(data);
        Assert.assertTrue(ch.writeOutbound(new Capsule(100, content)));

        ByteBuf out = ch.readOutbound();
        Assert.assertNotNull(out);
        // type=100 -> 2-byte varint: 0x4064
        Assert.assertEquals(0x40, out.readUnsignedByte());
        Assert.assertEquals(100, out.readUnsignedByte());
        // length=100 -> 2-byte varint: 0x4064
        Assert.assertEquals(0x40, out.readUnsignedByte());
        Assert.assertEquals(100, out.readUnsignedByte());
        // data
        for (int i = 0; i < 100; i++) {
            Assert.assertEquals((byte) i, out.readByte());
        }
        Assert.assertFalse(out.isReadable());
        out.release();
        ch.finish();
    }

    @Test
    public void testEncodeSingleCapsule4ByteVarInt() {
        EmbeddedChannel ch = new EmbeddedChannel(new CapsuleEncoder());
        int type = 20000;
        byte[] data = new byte[20000];
        ByteBuf content = Unpooled.wrappedBuffer(data);
        Assert.assertTrue(ch.writeOutbound(new Capsule(type, content)));

        ByteBuf out = ch.readOutbound();
        Assert.assertNotNull(out);
        // type=20000 -> 4-byte varint
        int b0 = out.readUnsignedByte();
        Assert.assertEquals(0x80, b0 & 0xC0);
        // read remaining 3 bytes of type varint and reconstruct
        int typeVal = (b0 & 0x3F) << 24 | (out.readUnsignedByte() << 16)
                | (out.readUnsignedByte() << 8) | out.readUnsignedByte();
        Assert.assertEquals(type, typeVal);
        // length=20000 -> 4-byte varint
        int l0 = out.readUnsignedByte();
        Assert.assertEquals(0x80, l0 & 0xC0);
        int lenVal = (l0 & 0x3F) << 24 | (out.readUnsignedByte() << 16)
                | (out.readUnsignedByte() << 8) | out.readUnsignedByte();
        Assert.assertEquals(20000, lenVal);
        Assert.assertEquals(20000, out.readableBytes());
        out.release();
        ch.finish();
    }

    @Test
    public void testEncodeEmptyPayload() {
        EmbeddedChannel ch = new EmbeddedChannel(new CapsuleEncoder());
        ByteBuf content = Unpooled.EMPTY_BUFFER;
        Assert.assertTrue(ch.writeOutbound(new Capsule(5, content)));

        ByteBuf out = ch.readOutbound();
        Assert.assertNotNull(out);
        // type=5 -> 1-byte varint
        Assert.assertEquals(5, out.readUnsignedByte());
        // length=0 -> 1-byte varint
        Assert.assertEquals(0, out.readUnsignedByte());
        Assert.assertFalse(out.isReadable());
        out.release();
        ch.finish();
    }

    // ==================== Round-trip (encoder -> decoder) ====================

    @Test
    public void testRoundTrip1ByteVarInt() {
        EmbeddedChannel ch = new EmbeddedChannel(new CapsuleEncoder(), new CapsuleDecoder(65536));
        byte[] data = "hello".getBytes();
        Capsule original = new Capsule(0, Unpooled.wrappedBuffer(data));

        Assert.assertTrue(ch.writeOutbound(original));
        ByteBuf encoded = ch.readOutbound();
        Assert.assertNotNull(encoded);
        Assert.assertTrue(ch.writeInbound(encoded));

        Capsule decoded = ch.readInbound();
        Assert.assertNotNull(decoded);
        Assert.assertEquals(0, decoded.type());
        byte[] result = new byte[decoded.content().readableBytes()];
        decoded.content().readBytes(result);
        Assert.assertArrayEquals(data, result);
        decoded.release();
        ch.finish();
    }

    @Test
    public void testRoundTrip2ByteVarInt() {
        EmbeddedChannel ch = new EmbeddedChannel(new CapsuleEncoder(), new CapsuleDecoder(65536));
        byte[] data = new byte[200];
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;
        Capsule original = new Capsule(100, Unpooled.wrappedBuffer(data));

        Assert.assertTrue(ch.writeOutbound(original));
        ByteBuf encoded = ch.readOutbound();
        Assert.assertTrue(ch.writeInbound(encoded));

        Capsule decoded = ch.readInbound();
        Assert.assertNotNull(decoded);
        Assert.assertEquals(100, decoded.type());
        Assert.assertEquals(200, decoded.content().readableBytes());
        byte[] result = new byte[200];
        decoded.content().readBytes(result);
        Assert.assertArrayEquals(data, result);
        decoded.release();
        ch.finish();
    }

    @Test
    public void testRoundTripMultipleCapsules() {
        // Encode on one channel, collect all bytes, decode on another
        EmbeddedChannel encCh = new EmbeddedChannel(new CapsuleEncoder());
        byte[] data1 = "first".getBytes();
        byte[] data2 = "second capsule".getBytes();

        encCh.writeOutbound(new Capsule(1, Unpooled.wrappedBuffer(data1)));
        encCh.writeOutbound(new Capsule(2, Unpooled.wrappedBuffer(data2)));

        ByteBuf encoded = Unpooled.buffer();
        ByteBuf chunk;
        while ((chunk = encCh.readOutbound()) != null) {
            encoded.writeBytes(chunk);
            chunk.release();
        }
        encCh.finish();

        EmbeddedChannel decCh = new EmbeddedChannel(new CapsuleDecoder(65536));
        Assert.assertTrue(decCh.writeInbound(encoded));

        Capsule c1 = decCh.readInbound();
        Assert.assertNotNull(c1);
        Assert.assertEquals(1, c1.type());
        byte[] r1 = new byte[c1.content().readableBytes()];
        c1.content().readBytes(r1);
        Assert.assertArrayEquals(data1, r1);
        c1.release();

        Capsule c2 = decCh.readInbound();
        Assert.assertNotNull(c2);
        Assert.assertEquals(2, c2.type());
        byte[] r2 = new byte[c2.content().readableBytes()];
        c2.content().readBytes(r2);
        Assert.assertArrayEquals(data2, r2);
        c2.release();

        Assert.assertNull(decCh.readInbound());
        decCh.finish();
    }
}
