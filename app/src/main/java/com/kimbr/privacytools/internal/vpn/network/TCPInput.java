package com.kimbr.privacytools.internal.vpn.network;

import android.util.Log;

import com.kimbr.privacytools.internal.LocalVpnService;
import com.kimbr.privacytools.internal.vpn.network.headers.TCPHeader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TCPInput implements Runnable {

    public static final String TAG = "TCPInput";
    private static final int HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.TCP_HEADER_SIZE;

    private ConcurrentLinkedQueue<ByteBuffer> outputQueue;
    private Selector selector;

    public TCPInput(ConcurrentLinkedQueue<ByteBuffer> outputQueue, Selector selector) {
        this.outputQueue = outputQueue;
        this.selector = selector;
    }

    @Override
    public void run() {
        try {
            Log.d(TAG, "Started.");
            while (!Thread.interrupted()) {
                final int readyChannels = selector.select();

                if (readyChannels == 0) {
                    Thread.sleep(10); // TODO: is this the most efficient way?
                    continue;
                }

                final Set<SelectionKey> keys = selector.selectedKeys();
                final Iterator<SelectionKey> keyIterator = keys.iterator();

                while (keyIterator.hasNext() && !Thread.interrupted()) {
                    final SelectionKey key = keyIterator.next();
                    if (key.isValid()) {
                        if (key.isConnectable()) processConnect(key, keyIterator);
                        else if (key.isReadable()) processInput(key, keyIterator);
                    }
                }
            }
        }

        catch (InterruptedException | IOException ex) {
            Log.d(TAG, ex.toString(), ex);
        }
    }

    private void processConnect(SelectionKey key, Iterator<SelectionKey> keyIterator) {
        final TCB tcb = (TCB) key.attachment();
        final Packet referencePacket = tcb.referencePacket;

        try {
            if (tcb.channel.finishConnect()) {
                keyIterator.remove();
                tcb.status = TCB.TCBStatus.SYN_RECEIVED;

                // TODO: set MSS for receiving larger packets from this device
                final ByteBuffer responseBuffer = ByteBufferPool.acquire();
                referencePacket.updateTcpBuffer(responseBuffer, (byte) (TCPHeader.SYN | TCPHeader.ACK), tcb.mySequenceNumber, tcb.myAcknowledgementNumber, 0);
                outputQueue.offer(responseBuffer);

                tcb.mySequenceNumber++; // SYN counts as a byte
                key.interestOps(SelectionKey.OP_READ);
            }
        }

        catch (IOException ex) {
            Log.e(TAG, "Connection Error: " + tcb.ipAndPort, ex);

            final ByteBuffer responseBuffer = ByteBufferPool.acquire();
            referencePacket.updateTcpBuffer(responseBuffer, (byte) TCPHeader.RST, 0, tcb.myAcknowledgementNumber, 0);
            outputQueue.offer(responseBuffer);
            TCB.closeTcb(tcb);
        }
    }

    private void processInput(SelectionKey key, Iterator<SelectionKey> keyIterator) {
        keyIterator.remove();
        final ByteBuffer receiveBuffer = ByteBufferPool.acquire();
        // Leave space for the header
        receiveBuffer.position(HEADER_SIZE);

        final TCB tcb = (TCB) key.attachment();
        synchronized (tcb) {
            final Packet referencePacket = tcb.referencePacket;
            final SocketChannel inputChannel = (SocketChannel) key.channel();

            int readBytes;
            try {
                readBytes = inputChannel.read(receiveBuffer);
            } catch (IOException ex) {
                Log.e(TAG, "Network Read Error: " + tcb.ipAndPort, ex);

                referencePacket.updateTcpBuffer(receiveBuffer, (byte) TCPHeader.RST, 0, tcb.myAcknowledgementNumber, 0);
                outputQueue.offer(receiveBuffer);
                TCB.closeTcb(tcb);
                return;
            }

            if (readBytes == -1) {
                // End of stream, stop waiting until we push more data
                key.interestOps(0);
                tcb.waitingForNetworkData = false;

                if (tcb.status != TCB.TCBStatus.CLOSE_WAIT) {
                    ByteBufferPool.release(receiveBuffer);
                    return;
                }

                tcb.status = TCB.TCBStatus.LAST_ACK;
                referencePacket.updateTcpBuffer(receiveBuffer, (byte) TCPHeader.FIN, tcb.mySequenceNumber, tcb.myAcknowledgementNumber, 0);
                tcb.myAcknowledgementNumber++; // FIN counts as a byte
            }

            else {
                // TODO: should ideally be splitting segments by MTU/MSS, but this seems to work without
                referencePacket.updateTcpBuffer(receiveBuffer, (byte) (TCPHeader.PSH | TCPHeader.ACK), tcb.mySequenceNumber, tcb.myAcknowledgementNumber, readBytes);
                tcb.mySequenceNumber += readBytes; // Next sequence number
                receiveBuffer.position(HEADER_SIZE + readBytes);
            }
        }

        outputQueue.offer(receiveBuffer);
    }
}
