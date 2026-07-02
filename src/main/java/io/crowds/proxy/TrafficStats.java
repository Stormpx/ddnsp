package io.crowds.proxy;

import io.netty.util.concurrent.Ticker;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public class TrafficStats {

    private final Ticker ticker;
    private final long startTimeNanos;

    private final LongAdder uploadBytes = new LongAdder();
    private final LongAdder downloadBytes = new LongAdder();

    private final Direction upload;
    private final Direction download;

    public TrafficStats(Ticker ticker) {
        this.ticker = ticker;
        this.startTimeNanos = ticker.nanoTime();
        long now = elapsedSeconds();
        this.upload = new Direction(now);
        this.download = new Direction(now);
    }

    public void upload(long bytes) {
        uploadBytes.add(bytes);
        upload.record(bytes);
    }

    public void download(long bytes) {
        downloadBytes.add(bytes);
        download.record(bytes);
    }

    public long totalUpload() {
        return uploadBytes.sum();
    }

    public long totalDownload() {
        return downloadBytes.sum();
    }

    public long avgUploadSpeed() {
        long elapsed = elapsedSeconds();
        return elapsed > 0 ? totalUpload() / elapsed : 0;
    }

    public long avgDownloadSpeed() {
        long elapsed = elapsedSeconds();
        return elapsed > 0 ? totalDownload() / elapsed : 0;
    }

    public long instantUploadSpeed() {
        return upload.instantSpeed();
    }

    public long instantDownloadSpeed() {
        return download.instantSpeed();
    }

    private long elapsedSeconds() {
        return TimeUnit.NANOSECONDS.toSeconds(ticker.nanoTime() - startTimeNanos);
    }

    private static class Snapshot {
        final long currentSecond;
        final long thisSecond;
        final long lastSpeed;

        Snapshot(long currentSecond, long thisSecond, long lastSpeed) {
            this.currentSecond = currentSecond;
            this.thisSecond = thisSecond;
            this.lastSpeed = lastSpeed;
        }
    }

    private class Direction {
        // single volatile reference guarantees atomic visibility of all fields
        private volatile Snapshot snapshot;
        // written by owning thread, read by query thread
        private volatile long thisSecond;

        Direction(long initialSecond) {
            this.snapshot = new Snapshot(initialSecond, 0, 0);
        }

        void record(long bytes) {
            Snapshot s = snapshot;
            long now = elapsedSeconds();
            if (now > s.currentSecond) {
                snapshot = new Snapshot(now, 0, thisSecond);
                thisSecond = 0;
            }
            thisSecond += bytes;
            // publish: snapshot.thisSecond is stale but consistent with lastSpeed
            // reader will see at most 1 second old data, never torn
        }

        long instantSpeed() {
            Snapshot s = snapshot;
            long now = elapsedSeconds();
            if (now > s.currentSecond) {
                // writer hasn't ticked yet, thisSecond is the latest data
                return thisSecond;
            }
            return s.lastSpeed;
        }
    }
}
