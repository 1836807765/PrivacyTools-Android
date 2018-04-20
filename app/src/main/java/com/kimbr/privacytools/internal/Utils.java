package com.kimbr.privacytools.internal;

import android.util.Log;

import java.io.Closeable;
import java.io.IOException;

public class Utils {

    public static void closeResources(Closeable... closeables) {
        for (Closeable resource : closeables) {
            try {
                Log.d("Utils", "Closing resource: " + resource.toString());
                resource.close();
            } catch (IOException ex) {
                Log.e("Utils.closeResource()", "Unable to close.", ex);
            }
        }
    }
}
