package com.kimbr.privacytools.internal.vpn;

import java.util.LinkedHashMap;

public class CustomLRUCache<K, V> extends LinkedHashMap<K, V> {

    private int maxSize;
    private CleanupCallback callback;

    public CustomLRUCache(int maxSize, CleanupCallback callback) {
        super(maxSize + 1, 1, true);
        this.maxSize = maxSize;
        this.callback = callback;
    }

    @Override
    protected boolean removeEldestEntry(Entry<K, V> eldest) {
        if (size() > maxSize) {
            callback.cleanup(eldest);
            return true;
        }

        return false;
    }

    public interface CleanupCallback<K, V> {
        void cleanup(Entry<K, V> eldest);
    }
}
