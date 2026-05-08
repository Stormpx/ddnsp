import io.crowds.util.SemVer;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class SemVerTest {

    @Test
    public void testBasic() {
        SemVer v = SemVer.parse("1.2.3");
        Assert.assertEquals(1, v.major());
        Assert.assertEquals(2, v.minor());
        Assert.assertEquals(3, v.patch());
        Assert.assertNull(v.preRelease());
        Assert.assertNull(v.build());
    }

    @Test
    public void testPreRelease() {
        SemVer v = SemVer.parse("1.0.0-alpha");
        Assert.assertEquals(1, v.major());
        Assert.assertEquals(0, v.minor());
        Assert.assertEquals(0, v.patch());
        Assert.assertEquals("alpha", v.preRelease());
        Assert.assertNull(v.build());
    }

    @Test
    public void testPreReleaseDotSeparated() {
        SemVer v = SemVer.parse("1.0.0-alpha.1");
        Assert.assertEquals("alpha.1", v.preRelease());
    }

    @Test
    public void testBuild() {
        SemVer v = SemVer.parse("1.0.0+build.123");
        Assert.assertEquals(1, v.major());
        Assert.assertEquals(0, v.minor());
        Assert.assertEquals(0, v.patch());
        Assert.assertNull(v.preRelease());
        Assert.assertEquals("build.123", v.build());
    }

    @Test
    public void testPreReleaseAndBuild() {
        SemVer v = SemVer.parse("1.0.0-beta+exp.sha.5114f85");
        Assert.assertEquals("beta", v.preRelease());
        Assert.assertEquals("exp.sha.5114f85", v.build());
    }

    @Test
    public void testPreReleaseAndBuildWithDot() {
        SemVer v = SemVer.parse("1.0.0-alpha.1+build.1");
        Assert.assertEquals("alpha.1", v.preRelease());
        Assert.assertEquals("build.1", v.build());
    }

    @Test
    public void testZeroVersion() {
        SemVer v = SemVer.parse("0.0.0");
        Assert.assertEquals(0, v.major());
        Assert.assertEquals(0, v.minor());
        Assert.assertEquals(0, v.patch());
    }

    @Test
    public void testLargeNumbers() {
        SemVer v = SemVer.parse("100.200.300");
        Assert.assertEquals(100, v.major());
        Assert.assertEquals(200, v.minor());
        Assert.assertEquals(300, v.patch());
    }

    @Test
    public void testPreReleaseWithHyphen() {
        SemVer v = SemVer.parse("1.0.0-alpha-beta");
        Assert.assertEquals("alpha-beta", v.preRelease());
    }

    @Test
    public void testPreReleaseNumericOnly() {
        SemVer v = SemVer.parse("1.0.0-1");
        Assert.assertEquals("1", v.preRelease());
    }

    @Test
    public void testBuildNumericOnly() {
        SemVer v = SemVer.parse("1.0.0+001");
        Assert.assertEquals("001", v.build());
    }

    @Test
    public void testBuildWithHyphen() {
        SemVer v = SemVer.parse("1.0.0+build-info.2");
        Assert.assertEquals("build-info.2", v.build());
    }

    // --- Invalid cases ---

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidLeadingZeroMajor() {
        SemVer.parse("01.0.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidLeadingZeroMinor() {
        SemVer.parse("1.01.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidLeadingZeroPatch() {
        SemVer.parse("1.0.01");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPreReleaseLeadingZero() {
        SemVer.parse("1.0.0-01");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidEmpty() {
        SemVer.parse("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidNull() {
        SemVer.parse(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidMissingPatch() {
        SemVer.parse("1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidMissingMinor() {
        SemVer.parse("1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidChar() {
        SemVer.parse("1.0.0-α");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidEmptyPreRelease() {
        SemVer.parse("1.0.0-");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidEmptyBuild() {
        SemVer.parse("1.0.0+");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPreReleaseDotStart() {
        SemVer.parse("1.0.0-.alpha");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPreReleaseDotEnd() {
        SemVer.parse("1.0.0-alpha.");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidBuildDotStart() {
        SemVer.parse("1.0.0+.build");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidBuildDotEnd() {
        SemVer.parse("1.0.0+build.");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidTrailingGarbage() {
        SemVer.parse("1.0.0abc");
    }

    @Test
    public void testSingleDigitComponents() {
        SemVer v = SemVer.parse("0.1.9");
        Assert.assertEquals(0, v.major());
        Assert.assertEquals(1, v.minor());
        Assert.assertEquals(9, v.patch());
    }

    @Test
    public void testPreReleaseComplex() {
        SemVer v = SemVer.parse("1.0.0-0.3.7");
        Assert.assertEquals("0.3.7", v.preRelease());
    }


    @Test
    public void testBuildWithHyphens() {
        SemVer v = SemVer.parse("1.0.0+21AF26D3--117B344092BD");
        Assert.assertEquals("21AF26D3--117B344092BD", v.build());
    }

    @Test
    public void testPreReleaseSingleHyphen() {
        SemVer v = SemVer.parse("1.0.0--");
        Assert.assertEquals("-", v.preRelease());
    }

    @Test
    public void testPreReleaseWithDigits() {
        SemVer v = SemVer.parse("1.0.0-1.2.3");
        Assert.assertEquals("1.2.3", v.preRelease());
    }

    @Test
    public void testBuildWithAllDigits() {
        SemVer v = SemVer.parse("1.0.0+123.456");
        Assert.assertEquals("123.456", v.build());
    }

    @Test
    public void testPreReleaseAlphaNumericMix() {
        SemVer v = SemVer.parse("1.0.0-1a.2b");
        Assert.assertEquals("1a.2b", v.preRelease());
    }

    @Test
    public void testComparableByVersion(){
        var list = List.of(
                SemVer.parse("1.0.0"),
                SemVer.parse("2.0.0"),
                SemVer.parse("2.1.0"),
                SemVer.parse("2.1.1"),
                SemVer.parse("2.1.15"),
                SemVer.parse("3.0.0")
        );

        for (int i = 0; i < list.size(); i++) {
            var v = list.get(i);
            for (int ii = i+1; ii < list.size(); ii++) {
                var v1 = list.get(ii);
                System.out.println(v+" vs "+v1);
                Assert.assertEquals(-1,v.compareTo(v1));
                Assert.assertEquals(1,v1.compareTo(v));
            }
        }
    }


    @Test
    public void testComparableBySamePrefix(){
        var list = List.of(
                SemVer.parse("1.0.0-0+build-ignored"),
                SemVer.parse("1.0.0-alpha"),
                SemVer.parse("1.0.0-alpha.1"),
                SemVer.parse("1.0.0-alpha.beta"),
                SemVer.parse("1.0.0-beta"),
                SemVer.parse("1.0.0-beta.2"),
                SemVer.parse("1.0.0-beta.11"),
                SemVer.parse("1.0.0-rc.1"),
                SemVer.parse("1.0.0"));

        for (int i = 0; i < list.size(); i++) {
            var v = list.get(i);
            for (int ii = i+1; ii < list.size(); ii++) {
                var v1 = list.get(ii);
                System.out.println(v+" < "+v1);
                Assert.assertEquals(-1,v.compareTo(v1));
                Assert.assertEquals(1,v1.compareTo(v));
            }
        }

    }
}
