package io.crowds.util;

//rfc6479
public class ReplayWindow {

    private final static int BLOCK_SIZE = 64;
    private final static int BITS_SHIFT  = 6;
    private final static int LOC_MASK = BLOCK_SIZE-1;

    private final int blocks;
    private final int windowsSize;
    private final int blockMask;

    private long[] bitmap;
    private long wt;


    public ReplayWindow(int blocks) {
        if (blocks<=2){
            throw new IllegalArgumentException("blocks must > 2");
        }
        if (Integer.bitCount(blocks)!=1){
            throw new IllegalArgumentException("blocks must pow of 2");
        }
        this.blocks = blocks;
        this.windowsSize = (blocks -1) * BLOCK_SIZE;
        this.blockMask = blocks -1;
        this.bitmap =new long[blocks];
    }

    public int getBlocks() {
        return blocks;
    }

    public int getWindowsSize(){
        return windowsSize;
    }

    public boolean check(long seq){
        if (seq>wt){
            return true;
        }
        if (wt- seq >windowsSize-1){
            return false;
        }
        int index = (int) ((seq >> BITS_SHIFT) & blockMask);

        return (bitmap[index]& 1L <<(seq&LOC_MASK))==0;

    }

    public boolean update(long seq){
        if (wt - seq > windowsSize-1)
            return false;

        int index = (int) (seq >> BITS_SHIFT);

        if (seq>wt){
            int wtIdx= (int) (wt >>BITS_SHIFT);
            int diff = index-wtIdx;
            if (diff > blocks){
                diff = blocks;
            }
            for (int i = wtIdx+1; i <= wtIdx+diff; i++) {
                bitmap[i& blockMask]=0;
            }
            wt=seq;
        }
        index&= blockMask;
        int location = (int) (seq&LOC_MASK);

        boolean zero = (bitmap[index]& 1L <<location)==0;

        bitmap[index]|= 1L <<location;

        return zero;
    }

}
//ccc10000|xxxxcccc|cccccccc|cccccccc