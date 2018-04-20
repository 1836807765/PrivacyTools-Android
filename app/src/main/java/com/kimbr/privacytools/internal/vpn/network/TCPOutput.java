package com.kimbr.privacytools.internal.vpn.network;

import android.util.Log;

import com.kimbr.privacytools.internal.LocalVpnService;
import com.kimbr.privacytools.internal.vpn.network.headers.TCPHeader;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TCPOutput implements Runnable {

    private static final String TAG = "TCPOutput";

    private LocalVpnService vpnService;
    private ConcurrentLinkedQueue<Packet> inputQueue;
    private ConcurrentLinkedQueue<ByteBuffer> outputQueue;
    private Selector selector;
    private Random random;

    public TCPOutput(ConcurrentLinkedQueue<Packet> inputQueue, ConcurrentLinkedQueue<ByteBuffer> outputQueue, Selector selector, LocalVpnService vpnService) {
        this.vpnService = vpnService;
        this.inputQueue = inputQueue;
        this.outputQueue = outputQueue;
        this.selector = selector;
        this.random = new Random();
    }

    @Override
    public void run() {
        try {
            Log.d(TAG, "Started.");

            final Thread currentThread = Thread.currentThread();
            while (true) {
                Packet currentPacket;

                // TODO: block when not connected
                do {
                    currentPacket = inputQueue.poll();
                    if (currentPacket != null) break;

                    Thread.sleep(10);
                } while (!currentThread.isInterrupted());

                if (currentThread.isInterrupted()) break;

                final ByteBuffer payloadBuffer = currentPacket.backingBuffer; // currentPacket should never be null
                currentPacket.backingBuffer = null;
                final ByteBuffer responseBuffer = ByteBufferPool.acquire();

                final InetAddress destinationAddress = currentPacket.ip4Header.destinationAddress;
                final TCPHeader tcpHeader = currentPacket.tcpHeader;
                final int destinationPort = tcpHeader.destinationPort;
                final int sourcePort = tcpHeader.sourcePort;

                final String ipAndPort = destinationAddress.getHostAddress() + ":" + destinationPort + ":" + sourcePort;
                final TCB tcb = TCB.getTcb(ipAndPort);

                if (tcb == null)
                    initializeConnection(ipAndPort, destinationAddress, destinationPort, currentPacket, tcpHeader, responseBuffer);

                else if (tcpHeader.isSYN())
                    processDuplicateSYN(tcb, tcpHeader, responseBuffer);

                else if (tcpHeader.isRST())
                    closeCleanly(tcb, responseBuffer);

                else if (tcpHeader.isFIN())
                    processFIN(tcb, tcpHeader, responseBuffer);

                else if (tcpHeader.isACK())
                    processACK(tcb, tcpHeader, payloadBuffer, responseBuffer);

                // TODO: cleanup later
                if (responseBuffer.position() == 0)
                    ByteBufferPool.release(responseBuffer);
                ByteBufferPool.release(payloadBuffer);
            }
        }

        catch (InterruptedException | IOException ex) {
            Log.e(TAG, "Stopping: " + ex.toString(), ex);
        }

        finally {
            TCB.closeAll();
        }
    }

    private void initializeConnection(String ipAndPort, InetAddress destinationAddress, int destinationPort, Packet currentPacket, TCPHeader tcpHeader, ByteBuffer responseBuffer) throws IOException {
        currentPacket.swapSourceAndDestination();

        if (tcpHeader.isSYN()) {
            final SocketChannel outputChannel = SocketChannel.open();
            outputChannel.configureBlocking(false);
            vpnService.protect(outputChannel.socket());

            final TCB tcb = new TCB(ipAndPort, random.nextInt(Short.MAX_VALUE + 1), tcpHeader.sequenceNumber, tcpHeader.sequenceNumber + 1, tcpHeader.acknowledgementNumber, outputChannel, currentPacket);
            TCB.putTcb(ipAndPort, tcb);

            try {
                outputChannel.connect(new InetSocketAddress(destinationAddress, destinationPort));

                if (outputChannel.finishConnect()) {
                    tcb.status = TCB.TCBStatus.SYN_RECEIVED;

                    // TODO: set MSS for receiving larger packets from the device
                    currentPacket.updateTcpBuffer(responseBuffer, (byte) (TCPHeader.SYN | TCPHeader.ACK), tcb.mySequenceNumber, tcb.myAcknowledgementNumber, 0);
                    tcb.mySequenceNumber++; // SYN counts as a byte
                }

                else {
                    tcb.status = TCB.TCBStatus.SYN_SENT;
                    selector.wakeup();
                    tcb.selectionKey = outputChannel.register(selector, SelectionKey.OP_CONNECT, tcb);
                    return;
                }
            }

            catch (IOException ex) {
                Log.e(TAG, "Connection Error: " + ipAndPort, ex);
                currentPacket.updateTcpBuffer(responseBuffer, (byte) TCPHeader.RST, 0, tcb.myAcknowledgementNumber, 0);
                TCB.closeTcb(tcb);
            }
        }

        else
            currentPacket.updateTcpBuffer(responseBuffer, (byte) TCPHeader.RST, 0, tcpHeader.sequenceNumber + 1, 0);

        outputQueue.offer(responseBuffer);
    }

    private void processDuplicateSYN(TCB tcb, TCPHeader tcpHeader, ByteBuffer responseBuffer) {
        synchronized (tcb) {
            if (tcb.status == TCB.TCBStatus.SYN_SENT) {
                tcb.myAcknowledgementNumber = tcpHeader.sequenceNumber + 1;
                return;
            }

            sendRST(tcb, 1, responseBuffer);
        }
    }

    private void processFIN(TCB tcb, TCPHeader tcpHeader, ByteBuffer responseBuffer) {
        synchronized (tcb) {
            final Packet referencePacket = tcb.referencePacket;
            tcb.myAcknowledgementNumber = tcpHeader.sequenceNumber + 1;
            tcb.theirAcknowledgementNumber = tcpHeader.acknowledgementNumber;

            if (tcb.waitingForNetworkData) {
                tcb.status = TCB.TCBStatus.CLOSE_WAIT;
                referencePacket.updateTcpBuffer(responseBuffer, (byte) TCPHeader.ACK, tcb.mySequenceNumber, tcb.myAcknowledgementNumber, 0);
            }

            else {
                tcb.status = TCB.TCBStatus.LAST_ACK;
                referencePacket.updateTcpBuffer(responseBuffer, (byte) (TCPHeader.FIN | TCPHeader.ACK), tcb.mySequenceNumber, tcb.myAcknowledgementNumber, 0);
                tcb.mySequenceNumber++;
            }
        }

        outputQueue.offer(responseBuffer);
    }

    private void processACK(TCB tcb, TCPHeader tcpHeader, ByteBuffer payloadBuffer, ByteBuffer responseBuffer) throws IOException {
        int payloadSize = payloadBuffer.limit() - payloadBuffer.position();

        synchronized (tcb) {
            final SocketChannel outputChannel = tcb.channel;

            if (tcb.status == TCB.TCBStatus.SYN_RECEIVED) {
                tcb.status = TCB.TCBStatus.ESTABLISHED;
                selector.wakeup();
                tcb.selectionKey = outputChannel.register(selector, SelectionKey.OP_READ, tcb);
                tcb.waitingForNetworkData = true;
            }

            else if (tcb.status == TCB.TCBStatus.LAST_ACK) {
                closeCleanly(tcb, responseBuffer);
                return;
            }

            if (payloadSize == 0) return; // Empty ACK, ignore

            if (!tcb.waitingForNetworkData) {
                selector.wakeup();
                tcb.selectionKey.interestOps(SelectionKey.OP_READ);
                tcb.waitingForNetworkData = true;
            }

            // Forward to remote server
            try {
                while (payloadBuffer.hasRemaining()) outputChannel.write(payloadBuffer);
            }

            catch (IOException ex) {
                Log.e(TAG, "Network write error: " + tcb.ipAndPort, ex);
                sendRST(tcb, payloadSize, responseBuffer);
                return;
            }

            // TODO: We don't expect out of order packets, but verify
            tcb.myAcknowledgementNumber = tcpHeader.sequenceNumber + payloadSize;
            tcb.theirAcknowledgementNumber = tcpHeader.acknowledgementNumber;
            final Packet referencePacket = tcb.referencePacket;
            referencePacket.updateTcpBuffer(responseBuffer, (byte) TCPHeader.ACK, tcb.mySequenceNumber, tcb.myAcknowledgementNumber, 0);
        }

        outputQueue.offer(responseBuffer);
    }

    private void sendRST(TCB tcb, int prevPayloadSize, ByteBuffer buffer) {
        tcb.referencePacket.updateTcpBuffer(buffer, (byte) TCPHeader.RST, 0, tcb.myAcknowledgementNumber + prevPayloadSize, 0);
        outputQueue.offer(buffer);
        TCB.closeTcb(tcb);
    }

    private void closeCleanly(TCB tcb, ByteBuffer buffer) {
        ByteBufferPool.release(buffer);
        TCB.closeTcb(tcb);
    }
}
