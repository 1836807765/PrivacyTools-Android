package com.kimbr.privacytools.internal.vpn.network;

import android.util.Log;

import com.kimbr.privacytools.internal.LocalVpnService;
import com.kimbr.privacytools.internal.vpn.CustomLRUCache;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UDPOutput implements Runnable {

    private static final String TAG = "UDPOutput";
    private static final int MAX_CACHE = 50;

    private LocalVpnService vpnService;
    private ConcurrentLinkedQueue<Packet> inputQueue;
    private Selector selector;
    private CustomLRUCache<String, DatagramChannel> channelCache = new CustomLRUCache<>(MAX_CACHE, new CustomLRUCache.CleanupCallback<String, DatagramChannel>() {
        @Override
        public void cleanup(Map.Entry<String, DatagramChannel> eldest) {
            closeChannel(eldest.getValue());
        }
    });

    public UDPOutput(ConcurrentLinkedQueue<Packet> inputQueue, Selector selector, LocalVpnService vpnService) {
        this.vpnService = vpnService;
        this.inputQueue = inputQueue;
        this.selector = selector;
    }

    @Override
    public void run() {
        Log.d(TAG, "Starting.");

        try {
            final Thread currentThread = Thread.currentThread();

            while (true) {
                Packet currentPacket;

                do {
                    currentPacket = inputQueue.poll();
                    if (currentPacket != null) break;

                    Thread.sleep(10);
                } while (!currentThread.isInterrupted());

                if (currentThread.isInterrupted()) break;

                final InetAddress destinationAddress = currentPacket.ip4Header.destinationAddress;
                final int destinationPort = currentPacket.udpHeader.destinationPort;
                final int sourcePort = currentPacket.udpHeader.sourcePort;

                final String ipAndPort = destinationAddress.getHostAddress() + ":" + destinationPort + ":" + sourcePort;
                DatagramChannel outputChannel = channelCache.get(ipAndPort);

                if (outputChannel == null) {
                    outputChannel = DatagramChannel.open();

                    try {
                        outputChannel.connect(new InetSocketAddress(destinationAddress, destinationPort));
                    }

                    catch (IOException ex) {
                        Log.e(TAG, "Connection Error: " + ipAndPort, ex);
                        closeChannel(outputChannel);
                        ByteBufferPool.release(currentPacket.backingBuffer);
                        continue;
                    }

                    outputChannel.configureBlocking(false);
                    currentPacket.swapSourceAndDestination();

                    selector.wakeup();
                    outputChannel.register(selector, SelectionKey.OP_READ, currentPacket);

                    vpnService.protect(outputChannel.socket());
                    channelCache.put(ipAndPort, outputChannel);
                }

                try {
                    final ByteBuffer payloadBuffer = currentPacket.backingBuffer;
                    while (payloadBuffer.hasRemaining()) outputChannel.write(payloadBuffer);
                }

                catch (IOException ex) {
                    Log.e(TAG, "Network Write Error: " + ipAndPort, ex);
                    channelCache.remove(ipAndPort);
                    closeChannel(outputChannel);
                }

                ByteBufferPool.release(currentPacket.backingBuffer);
            }
        }

        catch (InterruptedException | IOException ex) {
            Log.e(TAG, "Stopping: " + ex.toString(), ex);
        }

        finally {
            closeAll();
        }
    }

    private void closeAll() {
        final Iterator<Map.Entry<String, DatagramChannel>> iterator = channelCache.entrySet().iterator();

        while (iterator.hasNext()) {
            closeChannel(iterator.next().getValue());
            iterator.remove();
        }
    }

    private void closeChannel(DatagramChannel channel) {
        try {
            channel.close();
        }

        catch (IOException ex) {
            Log.e(TAG, "Failed to close channel: " + ex.toString(), ex);
        }
    }
}
