package top.kexin.minidb.backend.common;

import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import top.kexin.minidb.common.Error;

/**
 * A thread-safe, fixed-capacity, reference-counted cache for resources keyed by long IDs: calls to get(key)
 * perform single-flight loading (only one thread loads a missing key while others wait), pin the entry by
 * incrementing its reference count, and either return the cached value or throw CacheFullException when the
 * cache is at capacity; callers must later invoke release(key) to unpin the entry, and when the count drops
 * to zero the entry is evicted and releaseForCache(T) is invoked for write-back/cleanup. The cache can be
 * shut down via close(), which writes back and removes all entries. Subclasses implement getForCache(long)
 * to load a value and releaseForCache(T) to flush/close it; internal coordination uses a ReentrantLock and
 * simple “getting” markers to avoid duplicate loads.
 *
 * @author KexinDai
 * @date 2025/8/21
 */
public abstract class AbstractCache<T> {
    private HashMap<Long, T> cache;                     // data in cache
    private HashMap<Long, Integer> references;          // count of references
    private HashMap<Long, Boolean> getting;             // threads being getting

    private int maxResource;                            // max resources in cache
    private int count = 0;                              // count of data in cache
    private Lock lock;

    public AbstractCache(int maxResource) {
        this.maxResource = maxResource;
        cache = new HashMap<>();
        references = new HashMap<>();
        getting = new HashMap<>();
        lock = new ReentrantLock();
    }

    // get(long key) 的目标：拿到 key 对应的资源并把它“加引用”（pin）。若缓存里没有，它会只让一个线程去加载（single-flight），
    // 其余线程等待；容量满则抛 CacheFullException
    // single-flight（同一 key 只让一个线程去加载）。
    protected T get(long key) throws Exception {
        while(true) {
            lock.lock();
            if(getting.containsKey(key)) {
                // resource requested is in other threads
                lock.unlock();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue;
                }
                continue;
            }

            if(cache.containsKey(key)) {
                // resource in cache , return it
                T obj = cache.get(key);
                references.put(key, references.get(key) + 1);
                lock.unlock();
                return obj;
            }

            // 尝试获取该资源
            if(maxResource > 0 && count == maxResource) {
                lock.unlock();
                throw Error.CacheFullException;
            }
            count ++;
            getting.put(key, true);
            lock.unlock();
            break;
        }

        T obj = null;
        try {
            obj = getForCache(key);
        } catch(Exception e) {
            lock.lock();
            count --;
            getting.remove(key);
            lock.unlock();
            throw e;
        }

        lock.lock();
        getting.remove(key);
        cache.put(key, obj);
        // the first time to cache , set 1
        references.put(key, 1);
        lock.unlock();

        return obj;
    }

    /**
     * release a cache if no reference
     * else update references
     */
    protected void release(long key) {
        lock.lock();
        try {
            int ref = references.get(key)-1;
            if(ref == 0) {
                T obj = cache.get(key);
                releaseForCache(obj);
                references.remove(key);
                cache.remove(key);
                count --;
            } else {
                references.put(key, ref);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * close cache
     */
    protected void close() {
        lock.lock();
        try {
            Set<Long> keys = cache.keySet();
            for (long key : keys) {
                T obj = cache.get(key);
                releaseForCache(obj);
                references.remove(key);
                cache.remove(key);
            }
        } finally {
            lock.unlock();
        }
    }


    /**
     * get resource if not in cache
     */
    protected abstract T getForCache(long key) throws Exception;
    /**
     * write back when release resources
     */
    protected abstract void releaseForCache(T obj);
}

