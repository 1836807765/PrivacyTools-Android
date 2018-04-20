package com.kimbr.privacytools;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.ToggleButton;

import com.kimbr.privacytools.internal.LocalVpnService;
import com.kimbr.privacytools.internal.Preferences;
import com.kimbr.privacytools.internal.vpn.LoggingCallback;
import com.kimbr.privacytools.internal.vpn.network.Packet;

public class MainActivity extends Activity {

    private static final int VPN_REQUEST_CODE = 0x0F;

    private ToggleButton vpnButton;
    private ToggleButton loggingButton;

    private Intent vpnIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set subviews
        vpnButton = findViewById(R.id.button_vpn);
        loggingButton = findViewById(R.id.button_logging);

        // Get and set preferences
        final boolean loggingEnabled = Preferences.isLoggingEnabled();
        loggingButton.setChecked(loggingEnabled);

        // Setup vpn button listener
        vpnButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Initialize vpn if first-time running
                final Intent initialIntent = LocalVpnService.prepare(MainActivity.this);
                if (initialIntent != null) startActivityForResult(initialIntent, VPN_REQUEST_CODE);

                MainActivity.this.onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
            }
        });

        loggingButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                loggingButton.setEnabled(false);
                if (isChecked) LocalVpnService.Handler.setLoggingCallback(new LoggingCallback() {
                    @Override
                    public void log(Packet packet) {
                        Log.d("LoggingCallback", "Source:" + packet.ip4Header.sourceAddress.getHostName() + ", Destination:" + packet.ip4Header.destinationAddress.getHostName());
                    }
                });
                else LocalVpnService.Handler.setLoggingCallback(null);
                loggingButton.setEnabled(true);
            }
        });

        // Link buttons to LocalVpnService .isRunning and .loggingEnabled
        LocalVpnService.Handler.setRunningCallback(new LocalVpnService.IsRunningCallback() {
            @Override
            public void stateChanging() {
                vpnButton.setEnabled(false);
            }

            @Override
            public void started() {
                vpnButton.setChecked(true);
                vpnButton.setEnabled(true);
            }

            @Override
            public void stopped() {
                vpnButton.setChecked(false);
                vpnButton.setEnabled(true);
            }
        });

//         // Example of a call to a native method
//         final TextView textView = findViewById(R.id.sample_text);
//         textView.setText(stringFromJNI());
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {

            if (vpnIntent == null) {
                vpnIntent = new Intent(this, LocalVpnService.class);
                startService(vpnIntent);
            }

            else {
                LocalVpnService.Handler.stop();
                vpnIntent = null;
            }
        }
    }

//    /**
//     * A native method that is implemented by the 'native-lib' native library,
//     * which is packaged with this application.
//     */
//    public native String stringFromJNI();

//    // Used to load the 'native-lib' library on application startup.
//    static {
//        System.loadLibrary("native-lib");
//    }
}