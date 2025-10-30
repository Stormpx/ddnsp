package io.crowds.compoments.xdp;

import io.crowds.lib.xdp.Umem;
import io.netty.util.internal.shaded.org.jctools.queues.MpmcArrayQueue;

import java.util.Queue;

public class UmemBufferPoll {

    private final Umem umem;
    private final Queue<Block> blocks;

    public UmemBufferPoll(Umem umem,int blockSize) {
        this.umem = umem;
        this.blocks = new MpmcArrayQueue<>((this.umem.chunks()/blockSize)+1);
        initBlocks(blockSize);
    }

    private void initBlocks(int capacity){
        int totalChunks = this.umem.chunks();
        int chunkSize = this.umem.chunkSize();
        System.out.println(totalChunks);
        System.out.println(chunkSize);
        Block block = new Block(capacity);

        for (long i = 0; i < totalChunks; i++) {
            long addr = i*chunkSize;
            block.push(addr);
            if (block.isFull()){
                this.blocks.add(block);
                block = new Block((int) Math.min(totalChunks-(i+1),capacity));
            }
        }

        if (!block.isEmpty()&&block.capacity()>0){
            this.blocks.add(block);
        }
        System.out.println("bufferPoll: "+this.blocks.size());
    }

    public Umem umem() {
        return umem;
    }

    public Block get(){
        return blocks.poll();
    }

    public void put(Block block){
        if (!block.isFull()){
            throw new IllegalArgumentException("Container is not full");
        }
        blocks.add(block);
    }

}
