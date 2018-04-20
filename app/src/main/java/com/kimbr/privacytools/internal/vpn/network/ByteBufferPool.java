package com.kimbr.privacytools.internal.vpn.network;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ByteBufferPool {

    private static ConcurrentLinkedQueue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();
    private static int BUFFER_SIZE = 16384;

    public static ByteBuffer acquire() {
        ByteBuffer buffer = pool.poll();
        if (buffer == null) buffer = ByteBuffer.allocateDirect(BUFFER_SIZE); // Using direct buffer for zero-copy

        return buffer;
    }

    public static void release(ByteBuffer buffer) {
        buffer.clear();
        pool.offer(buffer);
    }

    public static void clear() {
        pool.clear();
    }
}
