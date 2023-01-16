package io.crowds.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class LRUK<K,V>  {
    private ReentrantLock lock;
    private int k;
    private SizedMap<V> pool;
    private Map<K,Integer> history;

    public LRUK(int k, int maxSize) {
        this.lock=new ReentrantLock();
        this.k = k;
        this.pool=new SizedMap<>(maxSize);
        this.history= new SizedMap<>(k * maxSize);
    }

    public void access(K key,V val){
        lock.lock();
        try {
            if (exists(key)){
                pool.access(key,val);
                return ;
            }
            Integer count = history.compute(key, (k, v) -> v == null ? 1 : v + 1);
            if (count>=k){
                history.remove(key);
                pool.access(key,val);
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean exists(K key){
        lock.lock();

        try {
            return pool.containsKey(key);
        } finally {
            lock.unlock();
        }
    }

    public V get(K key){
        lock.lock();

        try {
            V v = pool.get(key);
            if (v!=null)
                pool.access(key,v);
            return v;
        } finally {
            lock.unlock();
        }
    }

    class SizedMap<V> extends LinkedHashMap<K, V>{

        private Integer maxSize;

        public SizedMap(Integer maxSize) {
            this.maxSize = maxSize;
        }

        public void access(K k,V val){
            put(k,val);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size()> maxSize;
        }
    }
}
