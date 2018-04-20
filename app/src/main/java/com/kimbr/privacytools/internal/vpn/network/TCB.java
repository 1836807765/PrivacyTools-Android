package com.kimbr.privacytools.internal.vpn.network;

import android.util.Log;

import com.kimbr.privacytools.internal.vpn.CustomLRUCache;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;

public class TCB {

    private static final int MAX_CACHE_SIZE = 50; // TODO: is this ideal?
    private static CustomLRUCache<String, TCB> tcbCache = new CustomLRUCache<>(MAX_CACHE_SIZE, new CustomLRUCache.CleanupCallback<String, TCB>() {
        @Override
        public void cleanup(Map.Entry<String, TCB> eldest) {
            eldest.getValue().closeChannel();
        }
    });

    public String ipAndPort;
    public long mySequenceNumber;
    public long theirSequenceNumber;
    public long myAcknowledgementNumber;
    public long theirAcknowledgementNumber;
    public TCBStatus status;

    public Packet referencePacket;
    public SocketChannel channel;
    public boolean waitingForNetworkData;
    public SelectionKey selectionKey;

    // TCP has more states but we only need these
    public enum TCBStatus {
        SYN_SENT,
        SYN_RECEIVED,
        ESTABLISHED,
        CLOSE_WAIT,
        LAST_ACK
    }

    public TCB(String ipAndPort, long mySequenceNumber, long theirSequenceNumber, long myAcknowledgementNumber, long theirAcknowledgementNumber, SocketChannel channel, Packet referencePacket) {
        this.ipAndPort = ipAndPort;
        this.mySequenceNumber = mySequenceNumber;
        this.theirSequenceNumber = theirSequenceNumber;
        this.myAcknowledgementNumber = myAcknowledgementNumber;
        this.theirAcknowledgementNumber = theirAcknowledgementNumber;
        this.channel = channel;
        this.referencePacket = referencePacket;
    }

    public static TCB getTcb(String ipAndPort) {
        synchronized (tcbCache) {
            return tcbCache.get(ipAndPort);
        }
    }

    public static void putTcb(String ipAndPort, TCB tcb) {
        synchronized (tcbCache) {
            tcbCache.put(ipAndPort, tcb);
        }
    }

    public static void closeTcb(TCB tcb) {
        tcb.closeChannel();
        synchronized (tcbCache) {
            tcbCache.remove(tcb.ipAndPort);
        }
    }

    public static void closeAll() {
        synchronized (tcbCache) {
            final Iterator<Map.Entry<String, TCB>> iterator = tcbCache.entrySet().iterator();

            while (iterator.hasNext()) {
                iterator.next().getValue().closeChannel();
                iterator.remove();
            }
        }
    }

    private void closeChannel() {
        try {
            channel.close();
        }

        catch (IOException ex) {
            Log.e("TCB", ex.getMessage(), ex);
        }
    }
}
