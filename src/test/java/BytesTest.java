import io.crowds.util.Bufs;
import io.netty.buffer.Unpooled;
import org.junit.Assert;
import org.junit.Test;

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

}
