package io.crowds.compoments.xdp;

import java.util.NoSuchElementException;

public class Block {

    private final long[] chunks;
    private int idx;

    public Block(int size) {
        this.chunks = new long[size];
    }

    public int capacity(){
        return chunks.length;
    }

    public int size(){
        return idx;
    }

    public boolean isEmpty(){
        return size()==0;
    }

    public boolean isFull(){
        return size()==chunks.length;
    }

    public long poll(){
        if (isEmpty()){
            throw new NoSuchElementException();
        }
        return chunks[--idx];
    }

    public boolean push(long addr){
        if (isFull()){
            return false;
        }
        chunks[idx++] = addr;
        return true;
    }
}
