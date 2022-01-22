import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.crowds.proxy.ProxyContext;
import io.crowds.proxy.select.TransportProvider;
import io.crowds.proxy.select.TransportSelector;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProviderTest {

    private Map<String,TransportSelector> mock(Mock... mocks){

        return Arrays.stream(mocks)
                .collect(Collectors.toMap(Mock::getName, Function.identity()));

    }

    @Test
    public void dagTest(){

        Assert.assertTrue(new TransportProvider.RingDetector(Map.of()).searchCircularRef().isEmpty());

        Assert.assertTrue(new TransportProvider.RingDetector(
                mock(
                        new Mock("s1",List.of())
                )
        ).searchCircularRef().isEmpty());

        Assert.assertTrue(new TransportProvider.RingDetector(
                mock(
                        new Mock("s1",List.of("s2","s3")),
                        new Mock("s2",List.of("s3","t1")),
                        new Mock("s3",List.of("t2"))
                )
        ).searchCircularRef().isEmpty());

        Assert.assertTrue(new TransportProvider.RingDetector(mock(
                new Mock("A",List.of("C","E","F")),
                new Mock("B",List.of("A","C")),
                new Mock("C",List.of("t1")),
                new Mock("D",List.of("F")),
                new Mock("E",List.of("t1")),
                new Mock("F",List.of("E","G")),
                new Mock("G",List.of())
        )).searchCircularRef().isEmpty());

        Assert.assertTrue(new TransportProvider.RingDetector(mock(
                new Mock("2",List.of()),
                new Mock("3",List.of("8")),
                new Mock("5",List.of("11")),
                new Mock("7",List.of("11","8")),
                new Mock("8",List.of("9")),
                new Mock("9",List.of()),
                new Mock("10",List.of()),
                new Mock("11",List.of("2","9","10"))
        )).searchCircularRef().isEmpty());

        Assert.assertTrue(new TransportProvider.RingDetector(mock(
                new Mock("s1",List.of("s2","s3")),
                new Mock("s2",List.of("s3","t1")),
                new Mock("s3",List.of("t2","A")),

                new Mock("A",List.of("C","E","F")),
                new Mock("B",List.of("A","C")),
                new Mock("C",List.of("t1")),
                new Mock("D",List.of("F")),
                new Mock("E",List.of("t1")),
                new Mock("F",List.of("E","G")),
                new Mock("G",List.of("7")),

                new Mock("2",List.of()),
                new Mock("3",List.of("8")),
                new Mock("5",List.of("11")),
                new Mock("7",List.of("11","8")),
                new Mock("8",List.of("9")),
                new Mock("9",List.of()),
                new Mock("10",List.of()),
                new Mock("11",List.of("2","9","10"))
        )).searchCircularRef().isEmpty());

        Assert.assertTrue(new TransportProvider.RingDetector(
                mock(
                        new Mock("s1",List.of("s2","s3")),
                        new Mock("s2",List.of("s3","t1")),
                        new Mock("s3",List.of("t2")),
                        new Mock("A",List.of("C","E","F")),
                        new Mock("B",List.of("A","C")),
                        new Mock("C",List.of("t1")),
                        new Mock("D",List.of("F")),
                        new Mock("E",List.of("t1")),
                        new Mock("F",List.of("E","G")),
                        new Mock("G",List.of()),
                        new Mock("2",List.of()),
                        new Mock("3",List.of("8")),
                        new Mock("5",List.of("11")),
                        new Mock("7",List.of("11","8")),
                        new Mock("8",List.of("9")),
                        new Mock("9",List.of()),
                        new Mock("10",List.of()),
                        new Mock("11",List.of("2","9","10"))
        )).searchCircularRef().isEmpty());

        Assert.assertTrue(new TransportProvider.RingDetector(
                mock(
                        new Mock("C1",List.of("C2","C3","C4","C12")),
                        new Mock("C2",List.of("C3")),
                        new Mock("C3",List.of("C5","C7","C8")),
                        new Mock("C4",List.of("C5")),
                        new Mock("C5",List.of("C7")),
                        new Mock("C6",List.of("C8")),
                        new Mock("C7",List.of()),
                        new Mock("C8",List.of()),
                        new Mock("C9",List.of("C10","C11","C12")),
                        new Mock("C10",List.of("C12")),
                        new Mock("C11",List.of("C6")),
                        new Mock("C12",List.of())
        )).searchCircularRef().isEmpty());


    }


    private void assertEq(List<String> result,String... path){
        HashSet<String> set = new HashSet<>(Arrays.asList(path));
        Assert.assertEquals(set.size(),result.size());
        Assert.assertTrue(set.containsAll(result));
    }


    @Test
    public void ringTest(){

        assertEq(new TransportProvider.RingDetector(
                mock(
                        new Mock("A",List.of("B","D")),
                        new Mock("B",List.of("C")),
                        new Mock("C",List.of("E")),
                        new Mock("D",List.of()),
                        new Mock("E",List.of("B"))
                )).searchCircularRef(),
                "B","C","E");


        assertEq(new TransportProvider.RingDetector(
                        mock(
                                new Mock("A",List.of("A"))
                        )).searchCircularRef(),
                "A");

        assertEq(new TransportProvider.RingDetector(
                        mock(
                                new Mock("A",List.of("B")),
                                new Mock("B",List.of("A"))
                        )).searchCircularRef(),
                "A","B");

        assertEq(new TransportProvider.RingDetector(
                        mock(
                                new Mock("2",List.of()),
                                new Mock("3",List.of("8")),
                                new Mock("5",List.of("11")),
                                new Mock("B",List.of("A")),
                                new Mock("7",List.of("11","8")),
                                new Mock("8",List.of("9")),
                                new Mock("9",List.of()),
                                new Mock("10",List.of()),
                                new Mock("11",List.of("2","9","10")),
                                new Mock("A",List.of("B"))

                        )).searchCircularRef(),
                "A","B");

        assertEq(new TransportProvider.RingDetector(
                        mock(
                                new Mock("1",List.of("7")),
                                new Mock("2",List.of("7")),
                                new Mock("3",List.of("5")),
                                new Mock("4",List.of("5")),
                                new Mock("5",List.of("6")),
                                new Mock("6",List.of("8")),
                                new Mock("7",List.of("8")),
                                new Mock("8",List.of("8"))
                        )).searchCircularRef(),
                "8");


        assertEq(new TransportProvider.RingDetector(
                        mock(
                                new Mock("1",List.of("3")),
                                new Mock("2",List.of("3")),
                                new Mock("3",List.of("4")),
                                new Mock("4",List.of("5")),
                                new Mock("5",List.of("6")),
                                new Mock("6",List.of("8")),
                                new Mock("7",List.of("5")),
                                new Mock("8",List.of("4")),
                                new Mock("9",List.of("8")),
                                new Mock("10",List.of("6")),
                                new Mock("11",List.of("10")),
                                new Mock("12",List.of("10"))
                        )).searchCircularRef(),
                "4","5","6","8");


        assertEq(new TransportProvider.RingDetector(
                        mock(
                                new Mock("s1",List.of("s2","s3")),
                                new Mock("s2",List.of("s3","t1")),
                                new Mock("s3",List.of("t2","A")),

                                new Mock("A",List.of("C","E","F")),
                                new Mock("B",List.of("A","C")),
                                new Mock("C",List.of("t1")),
                                new Mock("D",List.of("F")),
                                new Mock("E",List.of("t1")),
                                new Mock("F",List.of("E","G")),
                                new Mock("G",List.of("C1")),

                                new Mock("C1",List.of("C2","C3","C4","C12")),
                                new Mock("C2",List.of("C3")),
                                new Mock("C3",List.of("C5","C7","C8")),
                                new Mock("C4",List.of("C5")),
                                new Mock("C5",List.of("C7")),
                                new Mock("C6",List.of("C8")),
                                new Mock("C7",List.of()),
                                new Mock("C8",List.of()),
                                new Mock("C9",List.of("C10","C11","C12")),
                                new Mock("C10",List.of("C12")),
                                new Mock("C11",List.of("C6")),
                                new Mock("C12",List.of()),

                                new Mock("1",List.of("3")),
                                new Mock("2",List.of("3")),
                                new Mock("3",List.of("4")),
                                new Mock("4",List.of("5")),
                                new Mock("5",List.of("6")),
                                new Mock("6",List.of("13")),
                                new Mock("7",List.of("5")),
                                new Mock("8",List.of("4")),
                                new Mock("9",List.of("8")),
                                new Mock("10",List.of("6")),
                                new Mock("11",List.of("10")),
                                new Mock("12",List.of("10")),
                                new Mock("13",List.of("14")),
                                new Mock("14",List.of("9"))
                        )).searchCircularRef(),
                "4","5","6","13","14","9","8");


    }



    private class Mock extends TransportSelector{

        private List<String> tags;

        public Mock(String name, List<String> tags) {
            super(name);
            this.tags = tags;
        }

        @Override
        public List<String> tags() {
            return tags;
        }

        @Override
        public String nextTag(ProxyContext proxyContext) {
            return null;
        }
    }

}
