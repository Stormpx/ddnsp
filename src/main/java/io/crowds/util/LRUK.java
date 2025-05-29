package io.crowds.util;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

public class LRUK<K,V>  {
    private final int maxSize;
    private final int k;
    private final ReentrantLock lock;
    private final LinkedHashMap<K,V> pool;
    private final LinkedHashMap<K,Integer> history;

    public LRUK(int k, int maxSize) {
        this.maxSize = maxSize;
        this.k = k;
        this.lock=new ReentrantLock();
        this.pool=new LinkedHashMap<>(16,0.75f,true);
        this.history=new LinkedHashMap<>();
    }

    private void evict(){
        if (!history.isEmpty()){
            pool.remove(history.pollFirstEntry().getKey());
        }else{
            pool.pollFirstEntry();
        }
    }

    //promote if key.access_count < k
    private void tryPromote(K key){
        Integer count = history.computeIfPresent(key,(k,c)->c+1);
        if (count!=null&&count>=k){
            history.remove(key);
        }
    }

    public void put(K key, V val){
        Objects.requireNonNull(val);
        lock.lock();
        try {
            V v = pool.get(key);
            if (v!=null){
                tryPromote(key);

                if (v!=val){
                    pool.replace(key,val);
                }
            }else {
                if (pool.size() == maxSize) {
                    evict();
                }
                pool.put(key, val);
                Integer count = history.compute(key, (k, c) -> c == null ? 1 : c + 1);
                if (count >= k) {
                    history.remove(key);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean exists(K key){
        return get(key)!=null;
    }

    public V get(K key){
        lock.lock();

        try {
            V v = pool.get(key);
            if (v!=null){
                tryPromote(key);
            }
            return v;
        } finally {
            lock.unlock();
        }
    }

}
