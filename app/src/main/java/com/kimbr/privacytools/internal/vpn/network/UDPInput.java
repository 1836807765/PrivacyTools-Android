package com.kimbr.privacytools.internal.vpn.network;

import android.util.Log;

import com.kimbr.privacytools.internal.LocalVpnService;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UDPInput implements Runnable {

    private static final String TAG = "UDPInput";
    private static final int HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.UDP_HEADER_SIZE;

    private Selector selector;
    private ConcurrentLinkedQueue<ByteBuffer> outputQueue;

    public UDPInput(ConcurrentLinkedQueue<ByteBuffer> outputQueue, Selector selector) {
        this.selector = selector;
        this.outputQueue = outputQueue;
    }

    @Override
    public void run() {
        try {
            Log.d(TAG, "Started.");

            while (!Thread.interrupted()) {
                final int readyChannels = selector.select();

                if (readyChannels == 0) {
                    Thread.sleep(10);
                    continue;
                }

                final Set<SelectionKey> keys = selector.selectedKeys();
                final Iterator<SelectionKey> keyIterator = keys.iterator();

                while (keyIterator.hasNext() && !Thread.interrupted()) {
                    final SelectionKey key = keyIterator.next();

                    if (key.isValid() && key.isReadable()) {
                        keyIterator.remove();

                        final ByteBuffer receivedBuffer = ByteBufferPool.acquire();
                        // Leave space for the header
                        receivedBuffer.position(HEADER_SIZE);

                        // TODO: possibly catch IOException and deal with here?
                        final DatagramChannel inputChannel = (DatagramChannel) key.channel();

                        int readBytes;
                        try {
                            readBytes = inputChannel.read(receivedBuffer);
                        }

                        catch (IOException ex) {
                            Log.e(TAG, "Network Read Error: " + ex.toString(), ex);
                            readBytes = -1;
                        }

                        if (readBytes != -1) {
                            final Packet referencePacket = (Packet) key.attachment();
                            referencePacket.updateUdpBuffer(receivedBuffer, readBytes);
                            receivedBuffer.position(HEADER_SIZE + readBytes);
                        }

                        outputQueue.offer(receivedBuffer);
                    }
                }
            }
        }

        catch (InterruptedException | IOException ex) {
            Log.e(TAG, "Stopped: " + ex.toString(), ex);
        }
    }
}
