package com.kimbr.privacytools.internal;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import com.kimbr.privacytools.MainActivity;
import com.kimbr.privacytools.R;
import com.kimbr.privacytools.internal.vpn.LoggingCallback;
import com.kimbr.privacytools.internal.vpn.network.ByteBufferPool;
import com.kimbr.privacytools.internal.vpn.network.Packet;
import com.kimbr.privacytools.internal.vpn.VpnRunnable;
import com.kimbr.privacytools.internal.vpn.network.TCPInput;
import com.kimbr.privacytools.internal.vpn.network.TCPOutput;
import com.kimbr.privacytools.internal.vpn.network.UDPInput;
import com.kimbr.privacytools.internal.vpn.network.UDPOutput;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalVpnService extends VpnService {

    private static volatile LocalVpnService instance; // TODO: improve performance of holding onto it's own reference?
    private static IsRunningCallback isRunningCb;
    private static LoggingCallback loggingCallback;

    private static String TAG = "LocalVpnService";
    private static String VPN_ADDRESS = "10.1.10.1"; // TODO: improve, is only ipv4 for now
    private static String VPN_ROUTE = "0.0.0.0"; // Intercepts everything

    private static String DNS_ADDRESS_0 = "1.1.1.1";
    private static String DNS_ADDRESS_1 = "1.0.0.1";

    private ExecutorService tunnelExecutorService;
    private ParcelFileDescriptor vpnInterface;

    private ConcurrentLinkedQueue<Packet> deviceToNetworkUdpQueue;
    private ConcurrentLinkedQueue<Packet> deviceToNetworkTcpQueue;
    private ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue;
    private Selector udpSelector;
    private Selector tcpSelector;

    @Override
    public void onCreate() {
        super.onCreate();

        if (instance == null) {
            synchronized (LocalVpnService.class) {
                if (instance == null) instance = this;
            }
        }

        startVpn();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopVpn();
        instance = null;
        Toast.makeText(this, "VPN stopped.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "VPN starting.", Toast.LENGTH_SHORT).show();
        return START_NOT_STICKY;
    }

    private void startVpn() {
        Log.d(TAG, "Starting.");
        if (isRunningCb != null) isRunningCb.stateChanging();

        setupVpn();
        try {
            udpSelector = Selector.open();
            tcpSelector = Selector.open();
            deviceToNetworkUdpQueue = new ConcurrentLinkedQueue<>();
            deviceToNetworkTcpQueue = new ConcurrentLinkedQueue<>();
            networkToDeviceQueue = new ConcurrentLinkedQueue<>();

            tunnelExecutorService = Executors.newFixedThreadPool(5);
            tunnelExecutorService.submit(new UDPInput(networkToDeviceQueue, udpSelector));
            tunnelExecutorService.submit(new UDPOutput(deviceToNetworkUdpQueue, udpSelector, this));
            tunnelExecutorService.submit(new TCPInput(networkToDeviceQueue, tcpSelector));
            tunnelExecutorService.submit(new TCPOutput(deviceToNetworkTcpQueue, networkToDeviceQueue, tcpSelector, this));

            final VpnRunnable vpnRunnable = new VpnRunnable(vpnInterface.getFileDescriptor(), deviceToNetworkUdpQueue, deviceToNetworkTcpQueue, networkToDeviceQueue);
            vpnRunnable.loggingCallback = loggingCallback; // reduces having to use an 'if' to check for loggingCallback != null
            tunnelExecutorService.submit(vpnRunnable);

            if (isRunningCb != null) isRunningCb.started();
        }

        catch (IOException ex) {
            // TODO: here and elsewhere should explicitly notify user of errors and suggest they stop the service
            Log.e(TAG, "Error starting service", ex);
            Toast.makeText(this, "Error starting VPN", Toast.LENGTH_SHORT).show();

            stopVpn();
            stopSelf();
        }
    }

    private void stopVpn() {
        if (isRunningCb != null) isRunningCb.stateChanging();

        tunnelExecutorService.shutdownNow();
        cleanup();

        if (isRunningCb != null) isRunningCb.stopped();
        Log.d(TAG, "Stopped");
    }

    private void setupVpn() {
        if (vpnInterface == null) {
            final Builder builder = new Builder();
            builder.setSession(getString(R.string.app_name));
            builder.addAddress(VPN_ADDRESS, 32);
//            builder.addDnsServer(DNS_ADDRESS_0);
//            builder.addDnsServer(DNS_ADDRESS_1);
            builder.addRoute(VPN_ROUTE, 0);
            builder.setConfigureIntent(getPendingIntent());
            vpnInterface = builder.establish();
        }
    }

    private PendingIntent getPendingIntent() {
        final Intent intent = new Intent(this, MainActivity.class);
        return PendingIntent.getActivity(this, 0, intent, 0);
    }

    private void cleanup() {
        deviceToNetworkUdpQueue = null;
        deviceToNetworkTcpQueue = null;
        networkToDeviceQueue = null;
        ByteBufferPool.clear();
        Utils.closeResources(udpSelector, tcpSelector, vpnInterface);
    }

    public static class Handler {

        public static void setRunningCallback(IsRunningCallback callback) {
            isRunningCb = callback;
        }

        public static void setLoggingCallback(LoggingCallback callback) {
            loggingCallback = callback;

            if (isRunning()) {
                Log.d(TAG, "Restarting VPN with logging enabled");
                instance.stopVpn();
                instance.startVpn();
            }
        }

        public static boolean isRunning() {
            return instance != null;
        }

        public static void stop() {
            instance.stopVpn();
            instance.stopSelf();
        }
    }

    // TODO: check if these interfaces being held in memory reduces performance
    public interface IsRunningCallback {
        void stateChanging();
        void started();
        void stopped();
    }

    // C++ methods
    private native void jni_start(long context);

    private native void jni_stop(long context);

    private native void jni_run(long context, int tun);

    private native void jni_clear(long context);
}
