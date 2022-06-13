import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.crowds.util.ReplayWindow;
import org.junit.Assert;
import org.junit.Test;

public class ReplayWindowsTest {


    @Test
    public void test(){
        ReplayWindow window = new ReplayWindow(4);
        System.out.println(window.getBlocks());
        System.out.println(window.getWindowsSize());
        Assert.assertEquals(192,window.getWindowsSize());

        for (int i = 0; i < 192; i++) {
            Assert.assertTrue(window.check(i));
            Assert.assertTrue(window.update(i));
            Assert.assertFalse(window.check(i));
            Assert.assertFalse(window.update(i));
        }
        Assert.assertTrue(window.check(384));
        Assert.assertTrue(window.update(384));
        Assert.assertFalse(window.check(384));
        for (int i = 0; i < 192; i++) {
            Assert.assertFalse(window.check(i));
            Assert.assertFalse(window.update(i));
        }
        for (int i = 383; i > 192; i--) {
            Assert.assertTrue(window.check(i));
            Assert.assertTrue(window.update(i));
        }
    }

    @Test
    public void test1(){

        for (int i = 2; i <= 7; i++) {
            int blocks = 1<<i;
            ReplayWindow window = new ReplayWindow(blocks);

            int windowsSize = window.getWindowsSize();
            for (long j = windowsSize; j < (long) windowsSize *windowsSize; j++) {
                Assert.assertTrue(window.check(j));
                Assert.assertTrue(window.update(j));
                Assert.assertFalse(window.check(j));
                Assert.assertFalse(window.update(j));

                Assert.assertFalse(window.update(j-windowsSize));

            }


        }



    }
}
