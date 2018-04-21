package com.kimbr.privacytools.internal.vpn;

import android.util.Log;

import com.kimbr.privacytools.internal.Utils;
import com.kimbr.privacytools.internal.vpn.network.ByteBufferPool;
import com.kimbr.privacytools.internal.vpn.network.Packet;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class VpnRunnable implements Runnable {

    private static final String TAG = "VpnRunnable";
    private final FileDescriptor fileDescriptor;

    private final ConcurrentLinkedQueue<Packet> deviceToNetworkUdpQueue;
    private final ConcurrentLinkedQueue<Packet> deviceToNetworkTcpQueue;
    private final ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue;

    public LoggingCallback loggingCallback;
    public Map<String, Boolean> filterMap;

    public VpnRunnable(FileDescriptor fileDescriptor, ConcurrentLinkedQueue<Packet> deviceToNetworkUdpQueue, ConcurrentLinkedQueue<Packet> deviceToNetworkTcpQueue, ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue) {
        this.fileDescriptor = fileDescriptor;
        this.deviceToNetworkUdpQueue = deviceToNetworkUdpQueue;
        this.deviceToNetworkTcpQueue = deviceToNetworkTcpQueue;
        this.networkToDeviceQueue = networkToDeviceQueue;
    }

    @Override
    public void run() {
        final FileChannel vpnInput = new FileInputStream(fileDescriptor).getChannel();
        final FileChannel vpnOutput = new FileOutputStream(fileDescriptor).getChannel();
        final boolean loggingEnabled = loggingCallback != null;
        final boolean filteringEnabled = filterMap != null;

        try {
            ByteBuffer bufferToNetwork = null;
            boolean dataSent = true;
            boolean dataReceived;

            // Loop to take if traffic filtering is enabled
            if (filteringEnabled) {
                Boolean filterResult;

                while (true) {
                    if (dataSent) bufferToNetwork = ByteBufferPool.acquire();
                    else bufferToNetwork.clear();

                    // TODO: Block when not connected
                    final int readBytes = vpnInput.read(bufferToNetwork);
                    if (readBytes > 0) {
                        dataSent = true;
                        filterResult = null;
                        bufferToNetwork.flip();

                        final Packet packet = new Packet(bufferToNetwork);
                        if (filterMap != null) {
                            final String hostUrl = packet.ip4Header.destinationAddress.getHostName();
                            filterResult = filterMap.containsKey(hostUrl) ? filterMap.get(hostUrl) : null;

                            if (filterResult == null || filterResult) {
                                if (packet.isUdp())
                                    deviceToNetworkUdpQueue.offer(packet);
                                else if (packet.isTcp())
                                    deviceToNetworkTcpQueue.offer(packet);
                            }
                        }

                        else {
                            Log.w(TAG, "Unknown packet type");
                            Log.w(TAG, packet.ip4Header.toString());
                            dataSent = false;
                        }

                        if (dataSent && loggingEnabled) loggingCallback.log(packet, filterResult);
                    } else dataSent = false;

                    final ByteBuffer bufferFromNetwork = networkToDeviceQueue.poll();
                    if (bufferFromNetwork != null) {
                        bufferFromNetwork.flip();

                        while (bufferFromNetwork.hasRemaining())
                            vpnOutput.write(bufferFromNetwork);

                        dataReceived = true;
                        ByteBufferPool.release(bufferFromNetwork);
                    } else dataReceived = false;

                    // TODO: sleep-looping not battery friendly. Block instead
                    if (!dataReceived && !dataSent) Thread.sleep(10);
                }
            }

            // Loop to take if traffic filtering disabled
            else {
                while (true) {
                    if (dataSent) bufferToNetwork = ByteBufferPool.acquire();
                    else bufferToNetwork.clear();

                    // TODO: Block when not connected
                    final int readBytes = vpnInput.read(bufferToNetwork);
                    if (readBytes > 0) {
                        dataSent = true;
                        bufferToNetwork.flip();

                        final Packet packet = new Packet(bufferToNetwork);

                        if (packet.isUdp())
                            deviceToNetworkUdpQueue.offer(packet);

                        else if (packet.isTcp())
                            deviceToNetworkTcpQueue.offer(packet);

                        else {
                            Log.w(TAG, "Unknown packet type");
                            Log.w(TAG, packet.ip4Header.toString());
                            dataSent = false;
                        }

                        if (dataSent && loggingEnabled) loggingCallback.log(packet, null);
                    } else dataSent = false;

                    final ByteBuffer bufferFromNetwork = networkToDeviceQueue.poll();
                    if (bufferFromNetwork != null) {
                        bufferFromNetwork.flip();

                        while (bufferFromNetwork.hasRemaining())
                            vpnOutput.write(bufferFromNetwork);

                        dataReceived = true;
                        ByteBufferPool.release(bufferFromNetwork);
                    } else dataReceived = false;

                    // TODO: sleep-looping not battery friendly. Block instead
                    if (!dataReceived && !dataSent) Thread.sleep(10);
                }
            }
        }

        catch (InterruptedException ex) {
            Log.e(TAG, "Stopping vpn runnable", ex);
        }

        catch (IOException ex) {
            Log.e(TAG, "Vpn runnable IOException", ex);
        }

        finally {
            Utils.closeResources(vpnInput, vpnOutput);
        }
    }
}
