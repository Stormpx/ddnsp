package io.crowds.util;


import org.junit.Assert;
import org.junit.Test;

public class LRUKTest {


    @Test
    public void test(){

        LRUK<Integer,Integer> lruk = new LRUK<>(2, 3);

        lruk.put(1,1);
        lruk.put(2,1);
        lruk.put(3,1);

        lruk.get(1);
        lruk.put(4,1);
        lruk.get(1);

        Assert.assertNull(lruk.get(2));
        Assert.assertEquals((Integer) 1,lruk.get(1));
    }

}