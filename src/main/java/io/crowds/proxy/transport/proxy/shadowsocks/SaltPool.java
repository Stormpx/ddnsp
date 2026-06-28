package io.crowds.proxy.transport.proxy.shadowsocks;

import io.crowds.util.Ints;
import io.netty.channel.EventLoop;

import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SaltPool {

    private static final HexFormat HEX = HexFormat.of();

    private final ConcurrentHashMap<String,Long> map;
    private final TreeMap<Long,String> tsMap;
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

        Map.Entry<Long,String> entry;
        while ((entry=tsMap.firstEntry()) != null && entry.getKey()<expireTimestamp){
            map.remove(entry.getValue());
            tsMap.remove(entry.getKey());
        }

    }

    private void put(String saltHex,long timestamp){
        eventLoop.execute(()->{
            map.put(saltHex,timestamp);
            tsMap.put(timestamp,saltHex);
        });
    }

    public boolean against(byte[] salt, long timestamp){
        String saltHex = HEX.formatHex(salt);
        Long ts = map.get(saltHex);
        if (ts!=null&& Ints.diff(timestamp,ts)>60){
            return false;
        }
        put(saltHex, timestamp);
        return true;
    }

}
