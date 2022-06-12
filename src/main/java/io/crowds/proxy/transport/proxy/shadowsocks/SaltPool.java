package io.crowds.proxy.transport.proxy.shadowsocks;

import io.crowds.util.Ints;
import io.netty.channel.EventLoop;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SaltPool {

    private final ConcurrentHashMap<byte[],Long> map;
    private final TreeMap<Long,byte[]> tsMap;
    private EventLoop eventLoop;

    public SaltPool(EventLoop eventLoop) {
        this.map=new ConcurrentHashMap<>();
        this.tsMap=new TreeMap<>();
        this.eventLoop = eventLoop;
        this.eventLoop.scheduleAtFixedRate(this::clearExpiredSalt,10,10, TimeUnit.SECONDS);
    }

    private void clearExpiredSalt(){
        long now = System.currentTimeMillis()/1000;
        long expireTimestamp = now-60;

        Map.Entry<Long,byte[]> entry;
        while ((entry=tsMap.firstEntry()).getKey()<expireTimestamp){
            map.remove(entry.getValue());
            tsMap.remove(entry.getKey());
        }

    }

    private void put(byte[] salt,long timestamp){
        eventLoop.execute(()->{
            map.put(salt,timestamp);
            tsMap.put(timestamp,salt);
        });
    }

    public boolean against(byte[] salt, long timestamp){
        Long ts = map.get(salt);
        if (ts!=null&& Ints.diff(timestamp,ts)>60){
            return false;
        }
        put(salt, timestamp);
        return true;
    }

}
