package pocketbus.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class Memoizer<K, V> {
    private final Map<K, V> map;
    private final Lock readLock;
    private final Lock writeLock;

    protected Memoizer() {
        // Don't use LinkedHashMap. This is a performance-oriented class and we don't want overhead
        this.map = new HashMap<>();
        ReadWriteLock lock = new ReentrantReadWriteLock();
        this.readLock = lock.readLock();
        this.writeLock = lock.writeLock();
    }

    public final V get(K key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }

        // check to see if we already have a value
        readLock.lock();
        try {
            V value = map.get(key);
            if (value != null) {
                return value;
            }
        } finally {
            readLock.unlock();
        }

        // create a new value.  this may race and we might create more than one instance, but that's ok
        V newValue = create(key);
        if (newValue == null) {
            throw new NullPointerException("create returned null");
        }

        // write the new value and return it
        writeLock.lock();
        try {
            map.put(key, newValue);
            return newValue;
        } finally {
            writeLock.unlock();
        }
    }

    protected abstract V create(K key);

    @Override public final String toString() {
        readLock.lock();
        try {
            return map.toString();
        } finally {
            readLock.unlock();
        }
    }
}
