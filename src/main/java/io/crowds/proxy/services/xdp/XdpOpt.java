package io.crowds.proxy.services.xdp;

import io.crowds.lib.xdp.ffi.Xsk;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class XdpOpt {
    private int threads=1;
    private int queue=1;
    private int mode=1;
    private boolean unload=false;
    private boolean rxCheck=true;
    private boolean txChecksum=false;
    private boolean busyPoll = false;
    private int umemSize = 4096 * Xsk.XSK_UMEM__DEFAULT_FRAME_SIZE;
    private int frameSize = Xsk.XSK_UMEM__DEFAULT_FRAME_SIZE;
    private int fillSize = Xsk.XSK_RING_PROD__DEFAULT_NUM_DESCS * 2;
    private int compSize = Xsk.XSK_RING_PROD__DEFAULT_NUM_DESCS * 2;
    private int rxSize = Xsk.XSK_RING_CONS__DEFAULT_NUM_DESCS;
    private int txSize = Xsk.XSK_RING_PROD__DEFAULT_NUM_DESCS;
    private List<String> bypassIps;

    public static XdpOpt of(JsonObject json){
        XdpOpt opt = new XdpOpt();
        var threads = json.getInteger("threads");
        var queue = json.getInteger("queue");
        var mode = json.getInteger("mode");
        var unload = json.getBoolean("unload");
        var rxCheck = json.getBoolean("rxCheck");
        var txChecksum = json.getBoolean("txChecksum");
        var busyPoll = json.getBoolean("busyPoll");
        var umemSize = json.getInteger("umemSize");
        var frameSize = json.getInteger("frameSize");
        var fillSize = json.getInteger("fillSize");
        var compSize = json.getInteger("compSize");
        var rxSize = json.getInteger("rxSize");
        var txSize = json.getInteger("txSize");
        JsonArray bypassArray = json.getJsonArray("bypassIps");
        if(threads!=null){
            opt.setThreads(threads);
        }
        if(queue!=null){
            opt.setQueue(queue);
        }
        if(mode!=null){
            opt.setMode(mode);
        }
        if(unload!=null){
            opt.setUnload(unload);
        }
        if (rxCheck!=null){
            opt.setRxCheck(rxCheck);
        }
        if (txChecksum!=null){
            opt.setTxChecksum(txChecksum);
        }
        if (busyPoll!=null){
            opt.setBusyPoll(busyPoll);
        }
        if(umemSize!=null){
            opt.setUmemSize(umemSize);
        }
        if(frameSize!=null){
            opt.setFrameSize(frameSize);
        }
        if(fillSize!=null){
            opt.setFillSize(fillSize);
        }
        if(compSize!=null){
            opt.setCompSize(compSize);
        }
        if(rxSize!=null){
            opt.setRxSize(rxSize);
        }
        if(txSize!=null){
            opt.setTxSize(txSize);
        }
        if (bypassArray!=null){
            List<String> bypassIps=new ArrayList<>();
            for (Object bypassIp : bypassArray) {
                if (bypassIp instanceof String s){
                    bypassIps.add(s);
                }
            }
            opt.setBypassIps(bypassIps);
        }
        return opt;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public int getQueue() {
        return queue;
    }

    public void setQueue(int queue) {
        this.queue = queue;
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public boolean isUnload() {
        return unload;
    }

    public void setUnload(boolean unload) {
        this.unload = unload;
    }


    public boolean isRxCheck() {
        return rxCheck;
    }

    public void setRxCheck(boolean rxCheck) {
        this.rxCheck = rxCheck;
    }

    public int getUmemSize() {
        return umemSize;
    }

    public void setUmemSize(int umemSize) {
        this.umemSize = umemSize;
    }

    public int getFrameSize() {
        return frameSize;
    }

    public void setFrameSize(int frameSize) {
        this.frameSize = frameSize;
    }

    public int getFillSize() {
        return fillSize;
    }

    public void setFillSize(int fillSize) {
        this.fillSize = fillSize;
    }

    public int getCompSize() {
        return compSize;
    }

    public void setCompSize(int compSize) {
        this.compSize = compSize;
    }

    public int getRxSize() {
        return rxSize;
    }

    public void setRxSize(int rxSize) {
        this.rxSize = rxSize;
    }

    public int getTxSize() {
        return txSize;
    }

    public void setTxSize(int txSize) {
        this.txSize = txSize;
    }

    public List<String> getBypassIps() {
        return bypassIps;
    }

    public void setBypassIps(List<String> bypassIps) {
        this.bypassIps = bypassIps;
    }

    @Override
    public String toString() {
        return Json.encode(this);
    }

    public boolean isBusyPoll() {
        return busyPoll;
    }

    public void setBusyPoll(boolean busyPoll) {
        this.busyPoll = busyPoll;
    }

    public boolean isTxChecksum() {
        return txChecksum;
    }

    public void setTxChecksum(boolean txChecksum) {
        this.txChecksum = txChecksum;
    }
}
