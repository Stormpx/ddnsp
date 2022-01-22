package io.crowds.proxy.select;

import io.crowds.proxy.ProxyContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class WRR extends TransportSelector {
    private List<String> tags;

    private AtomicInteger cursor;
    private int[] seq;

    public WRR(String name,List<WNode> nodes) {
        super(name);
        Objects.requireNonNull(nodes);
        assert !nodes.isEmpty();
        calculate(nodes);
        this.cursor=new AtomicInteger(0);
        this.tags=nodes.stream().map(WNode::tag).collect(Collectors.toList());
    }

    private boolean isFinish(int[] weights){
        for (int weight : weights) {
            if (weight != 0) {
                return false;
            }
        }
        return true;
    }

    private int maxIdx(int[] weights){
        assert weights.length>0;
        int idx=0;
        for (int i = 1; i < weights.length; i++) {
            if (weights[i]>weights[idx]){
                idx=i;
            }
        }
        return idx;
    }

    private void calculate(List<WNode> nodes){

        int sum=0;
        int[] currentWeights=new int[nodes.size()];

        for (WNode node : nodes) {
            sum += node.weight;
        }

        if (sum==0){
            this.seq=new int[0];
            return;
        }
        List<Integer> nodeSeqList=new ArrayList<>();
        do {
            for (int i = 0; i < currentWeights.length; i++) {
                currentWeights[i] += nodes.get(i).weight;
            }

            int idx = maxIdx(currentWeights);
            nodeSeqList.add(idx);
            currentWeights[idx] -= sum;

        } while (!isFinish(currentWeights));

        this.seq=nodeSeqList.stream().mapToInt(i->i).toArray();
    }

    @Override
    public List<String> tags() {
        return tags;
    }

    @Override
    public String nextTag(ProxyContext proxyContext) {
        int index = cursor.getAndIncrement();
        if (index>= seq.length){
            index%=seq.length;
            cursor.set(index+1);
        }
        return tags.get(seq[index]);
    }

    public static record WNode(int weight, String tag){}

}
