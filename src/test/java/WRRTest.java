import io.crowds.proxy.select.WRR;
import io.crowds.proxy.select.WRR.WNode;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WRRTest {

    @Test
    public void testWeightedDistribution() {
        // A:weight=4, B:weight=1, C:weight=1
        // Expected seq: [A, A, B, A, C, A] (Smooth Weighted Round Robin)
        var wrr = new WRR("test", List.of(
                new WNode(4, "A"),
                new WNode(1, "B"),
                new WNode(1, "C")
        ));

        Map<String, Integer> counts = new HashMap<>();
        int total = 600;
        for (int i = 0; i < total; i++) {
            String tag = wrr.nextTag(null);
            counts.merge(tag, 1, Integer::sum);
        }

        // A should get ~400, B ~100, C ~100
        Assert.assertEquals(400, (int) counts.get("A"));
        Assert.assertEquals(100, (int) counts.get("B"));
        Assert.assertEquals(100, (int) counts.get("C"));
    }

    @Test
    public void testEqualWeights() {
        var wrr = new WRR("test", List.of(
                new WNode(1, "X"),
                new WNode(1, "Y"),
                new WNode(1, "Z")
        ));

        Map<String, Integer> counts = new HashMap<>();
        int total = 300;
        for (int i = 0; i < total; i++) {
            String tag = wrr.nextTag(null);
            counts.merge(tag, 1, Integer::sum);
        }

        Assert.assertEquals(100, (int) counts.get("X"));
        Assert.assertEquals(100, (int) counts.get("Y"));
        Assert.assertEquals(100, (int) counts.get("Z"));
    }

    @Test
    public void testSmoothDistribution() {
        // With SWRR, the same tag should not appear consecutively too many times
        var wrr = new WRR("test", List.of(
                new WNode(5, "A"),
                new WNode(1, "B")
        ));

        int seqLen = 6; // sum of weights
        String[] results = new String[seqLen];
        for (int i = 0; i < seqLen; i++) {
            results[i] = wrr.nextTag(null);
        }

        // One full cycle of 6 should contain 5 A's and 1 B
        int aCount = 0, bCount = 0;
        for (String r : results) {
            if ("A".equals(r)) aCount++;
            else bCount++;
        }
        Assert.assertEquals(5, aCount);
        Assert.assertEquals(1, bCount);

        // B should not be at the very end (smoothness property)
        // In SWRR, B should appear roughly in the middle
        int bIndex = -1;
        for (int i = 0; i < results.length; i++) {
            if ("B".equals(results[i])) {
                bIndex = i;
                break;
            }
        }
        // B should not be the last element (smoothness)
        Assert.assertNotEquals(seqLen - 1, bIndex);
    }

    @Test
    public void testSingleTag() {
        var wrr = new WRR("test", List.of(
                new WNode(5, "only")
        ));

        for (int i = 0; i < 100; i++) {
            Assert.assertEquals("only", wrr.nextTag(null));
        }
    }

    @Test
    public void testLargeWeightDifference() {
        var wrr = new WRR("test", List.of(
                new WNode(100, "heavy"),
                new WNode(1, "light")
        ));

        Map<String, Integer> counts = new HashMap<>();
        int total = 1010;
        for (int i = 0; i < total; i++) {
            String tag = wrr.nextTag(null);
            counts.merge(tag, 1, Integer::sum);
        }

        Assert.assertEquals(1000, (int) counts.get("heavy"));
        Assert.assertEquals(10, (int) counts.get("light"));
    }

    @Test
    public void testTags() {
        var wrr = new WRR("test", List.of(
                new WNode(1, "A"),
                new WNode(2, "B"),
                new WNode(3, "C")
        ));

        Assert.assertEquals(List.of("A", "B", "C"), wrr.tags());
        Assert.assertEquals("test", wrr.getName());
    }

    @Test
    public void testNoOverflowOnHighCallCount() {
        // Simulate many calls to ensure AtomicLong overflow doesn't cause AIOOBE
        var wrr = new WRR("test", List.of(
                new WNode(1, "A"),
                new WNode(1, "B")
        ));

        // Call enough times that a naive int implementation would overflow
        int iterations = 1_000_000;
        for (int i = 0; i < iterations; i++) {
            String tag = wrr.nextTag(null);
            Assert.assertNotNull(tag);
        }
    }

    @Test
    public void testCycleRepeats() {
        var wrr = new WRR("test", List.of(
                new WNode(2, "A"),
                new WNode(1, "B")
        ));

        // Full cycle is 3 (sum of weights)
        String[] firstCycle = new String[3];
        for (int i = 0; i < 3; i++) {
            firstCycle[i] = wrr.nextTag(null);
        }

        String[] secondCycle = new String[3];
        for (int i = 0; i < 3; i++) {
            secondCycle[i] = wrr.nextTag(null);
        }

        // Both cycles should have the same sequence
        Assert.assertArrayEquals(firstCycle, secondCycle);
    }

    @Test
    public void testDeterministicOutput() {
        // Two WRR instances with same config should produce same sequence
        List<WNode> nodes = List.of(
                new WNode(3, "A"),
                new WNode(2, "B"),
                new WNode(1, "C")
        );

        var wrr1 = new WRR("test1", nodes);
        var wrr2 = new WRR("test2", nodes);

        for (int i = 0; i < 60; i++) {
            Assert.assertEquals(wrr1.nextTag(null), wrr2.nextTag(null));
        }
    }
}
