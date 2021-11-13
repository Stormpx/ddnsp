import io.crowds.util.Bufs;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

public class BytesTest {

    @Test
    public void testInt(){
        byte[] cmdBytes=new byte[4];
        for (int j = 0; j < 10000; j++) {
            int f= ThreadLocalRandom.current().nextInt();
            Bufs.writeInt(cmdBytes,0,f);
            Assert.assertEquals(Unpooled.wrappedBuffer(cmdBytes).readInt(),f);

            Assert.assertEquals(f,Bufs.getInt(cmdBytes,0));
        }
    }


    @Test
    public void hkdfSha1Test(){
        System.out.println(((byte)128 +2));


    }

    public static void main(String[] args) {
        ByteBuf buf = Unpooled.buffer(8);
        buf.writeInt(4);
        System.out.println(buf.nioBufferCount());
        ByteBuffer buffer = buf.nioBuffer(4,4);
        buffer.putInt(9);
        buf.writerIndex(8);
        System.out.println(buf.readableBytes());
        System.out.println(buf.readInt());
        System.out.println(buf.readInt());
        System.out.println(buffer);
    }
}
