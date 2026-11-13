package proxy;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.TP;
import io.crowds.proxy.transport.proxy.xudp.Mux;
import io.crowds.proxy.transport.proxy.xudp.MuxCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * Frame codec tests. No container is needed.
 */
public class MuxCodecTest {

    private static final byte[] GLOBAL_ID = {0x11,0x22,0x33,0x44,0x55,0x66,0x77,(byte)0x88};

    private static EmbeddedChannel channel(){
        return new EmbeddedChannel(new MuxCodec());
    }

    private static ByteBuf hex(String hex){
        return Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(hex.replaceAll("\\s","")));
    }

    private static String str(ByteBuf buf){
        return buf.toString(StandardCharsets.US_ASCII);
    }

    @Test
    public void newFrameTest(){
        // 00 14 | 00 00 | 01 | 01 | 02 | 00 35 | 01 | 01 02 03 04 | 11..88 | 00 05 | hello
        String vector = "0014 0000 01 01 02 0035 01 01020304 1122334455667788 0005 68656c6c6f";
        EmbeddedChannel channel = channel();

        assertTrue(channel.writeInbound(hex(vector)));
        Mux mux = channel.readInbound();
        assertTrue(mux instanceof Mux.New);
        Mux.New n = (Mux.New) mux;
        assertEquals(TP.UDP, n.network());
        assertEquals("1.2.3.4", n.addr().getHost());
        assertEquals(53, n.addr().getPort());
        assertArrayEquals(GLOBAL_ID, n.globalId());
        assertEquals("hello", str(n.data()));
        mux.release();

        assertTrue(channel.writeOutbound(new Mux.New(0, TP.UDP, NetAddr.of("1.2.3.4", 53), GLOBAL_ID,
                Unpooled.copiedBuffer("hello", StandardCharsets.US_ASCII))));
        ByteBuf out = channel.readOutbound();
        assertEquals(vector.replaceAll("\\s",""), ByteBufUtil.hexDump(out));
        out.release();

        channel.finishAndReleaseAll();
    }

    @Test
    public void keepWithoutTargetTest(){
        // a plain Keep with metaLen=4: decoding succeeds, network/addr are null, whether to accept it is up to the caller
        EmbeddedChannel channel = channel();
        assertTrue(channel.writeInbound(hex("0004 0000 02 01 0005 68656c6c6f")));
        Mux mux = channel.readInbound();
        assertTrue(mux instanceof Mux.Keep);
        Mux.Keep keep = (Mux.Keep) mux;
        assertNull(keep.network());
        assertNull(keep.addr());
        assertEquals("hello", str(keep.data()));
        mux.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void keepWithTargetTest(){
        // target override 5.6.7.8:443
        EmbeddedChannel channel = channel();
        assertTrue(channel.writeInbound(hex("000c 0000 02 01 02 01bb 01 05060708 0005 68656c6c6f")));
        Mux.Keep keep = channel.readInbound();
        assertEquals(TP.UDP, keep.network());
        assertEquals("5.6.7.8", keep.addr().getHost());
        assertEquals(443, keep.addr().getPort());
        assertEquals("hello", str(keep.data()));
        keep.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void keepAliveIsNotEndTest(){
        // a KeepAlive must not prevent the next frame from being decoded
        EmbeddedChannel channel = channel();
        assertTrue(channel.writeInbound(hex("0004 0000 04 00" + "0004 0000 02 01 0003 616263")));

        Mux first = channel.readInbound();
        assertTrue(first instanceof Mux.KeepAlive);
        first.release();

        Mux.Keep keep = channel.readInbound();
        assertEquals("abc", str(keep.data()));
        keep.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void endFrameTest(){
        EmbeddedChannel channel = channel();
        assertTrue(channel.writeInbound(hex("0004 0000 03 00")));
        Mux mux = channel.readInbound();
        assertTrue(mux instanceof Mux.End);
        assertNull(mux.data());
        mux.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void endWithErrorTest(){
        // option=0x03 (data+error): Xray only sets the error bit on End frames to signal an abnormal end
        EmbeddedChannel channel = channel();
        assertTrue(channel.writeInbound(hex("0004 0000 03 02")));
        Mux.End end = channel.readInbound();
        assertTrue(end.error());
        assertNull(end.data());
        end.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void keepIgnoresUnknownOptionBitsTest(){
        // the error bit on a Keep is not part of this implementation's semantics: it does not affect decoding
        EmbeddedChannel channel = channel();
        assertTrue(channel.writeInbound(hex("0004 0000 02 03 0004 6661696c")));
        Mux.Keep keep = channel.readInbound();
        assertEquals("fail", str(keep.data()));
        keep.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void noGlobalIdWithoutDataBitTest(){
        // without the data bit the GlobalID is not parsed (8 trailing bytes after the address are not
        // treated as a GlobalID, they are skipped as undefined trailing bytes)
        EmbeddedChannel channel = channel();
        assertTrue(channel.writeInbound(hex("0014 0000 01 00 02 0035 01 01020304 1122334455667788")));
        Mux.New n = channel.readInbound();
        assertNull(n.globalId());
        assertNull(n.data());
        n.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void longDomainTest(){
        String domain = "a".repeat(200);
        EmbeddedChannel channel = channel();
        assertTrue(channel.writeOutbound(new Mux.Keep(0, TP.UDP, NetAddr.of(domain, 53),
                Unpooled.copiedBuffer("x", StandardCharsets.US_ASCII))));
        ByteBuf encoded = channel.readOutbound();
        // the domain length is an unsigned byte: 200 has to survive a round trip
        assertTrue(channel.writeInbound(encoded));
        Mux.Keep keep = channel.readInbound();
        assertEquals(domain, keep.addr().getHost());
        assertEquals(53, keep.addr().getPort());
        keep.release();
        channel.finishAndReleaseAll();
    }

    @Test(expected = DecoderException.class)
    public void tooShortMetaLenTest(){
        channel().writeInbound(hex("0003 0000 02"));
    }

    @Test(expected = DecoderException.class)
    public void tooLongMetaLenTest(){
        channel().writeInbound(hex("0201 0000 02"));
    }

    @Test(expected = DecoderException.class)
    public void unknownStatusTest(){
        channel().writeInbound(hex("0004 0000 05 00"));
    }

    @Test(expected = DecoderException.class)
    public void unknownNetworkTest(){
        channel().writeInbound(hex("0008 0000 01 00 03 0035 01"));
    }

    @Test(expected = DecoderException.class)
    public void unknownAddressTypeTest(){
        channel().writeInbound(hex("000a 0000 01 00 02 0035 09 01020304"));
    }

    @Test(expected = DecoderException.class)
    public void keepAliveWithExtraMetaTest(){
        channel().writeInbound(hex("0005 0000 04 00 00"));
    }
}
