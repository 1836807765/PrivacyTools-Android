package com.kimbr.privacytools.internal.vpn;

import android.content.Context;
import android.util.Log;

import com.kimbr.privacytools.internal.Preferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class FilterMapHandler {

    // TODO: Eventually store downloaded hosts in SQL database
    // TODO: Also store whitelist / blacklist in SQL? Or just text file?

    private static final String TAG = "FilterMapHandler";
    private static final String HOSTS_FILE = "hosts.txt";
    private static final String WHITELIST_FILE = "whitelist.txt";
    private static final String BLACKLIST_FILE = "blacklist.txt";

    // Returns a filter map with String = host, and boolean = connectivityAllowed
    public static Map<String, Boolean> getMap(Context context) {
        final Map<String, Boolean> filterMap = new HashMap<>();

        // Get hosts (either from file or from url then to file)
        if (Preferences.useHostsFile()) {
            final File hostsFile = new File(context.getFilesDir(), HOSTS_FILE);
            if (!hostsFile.exists()) {
                final String hostsUrl = Preferences.getHostsUrl();
                final Map<String, Boolean> downloadedHosts = fetch(hostsUrl);
                writeToFile(hostsFile, downloadedHosts);
                filterMap.putAll(downloadedHosts);
            }

            else filterMap.putAll(readFromFile(hostsFile, false));
        }

        // Read whitelist
        final File whitelistFile = getWhitelistFile(context);
        if (whitelistFile.exists())
            filterMap.putAll(readFromFile(whitelistFile, true));
        // Read blacklist
        final File blacklistFile = getBlacklistFile(context);
        if (blacklistFile.exists())
            filterMap.putAll(readFromFile(blacklistFile, false));

        return filterMap;
    }

    public static File getWhitelistFile(Context context) {
        return new File(context.getFilesDir(), WHITELIST_FILE);
    }

    public static File getBlacklistFile(Context context) {
        return new File(context.getFilesDir(), BLACKLIST_FILE);
    }

    public static void updateHostsFile(Context context) {
        final String hostsUrl = Preferences.getHostsUrl();
        final File hostsFile = new File(context.getFilesDir(), HOSTS_FILE);
        final Map<String, Boolean> hosts = fetch(hostsUrl);
        writeToFile(hostsFile, hosts);
    }

    private static Map<String, Boolean> fetch(String hostsUrl) {
        try {
            final URL url = new URL(hostsUrl);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            final Map<String, Boolean> filterMap = new HashMap<>();

            String line;
            while ((line = reader.readLine()) != null) {
                if (Character.isLetterOrDigit(line.charAt(0))
                        || filterMap.containsKey(line))
                    continue;

                line = line.replaceAll("127.0.0.1 ", "");
                filterMap.put(line, false);
            }
            reader.close();

            return filterMap;
        }

        catch (MalformedURLException ex) {
            Log.e(TAG, "Invalid url.", ex);
            return new HashMap<>();
        }

        catch (IOException ex) {
            Log.e(TAG, "Unable to read from url's input stream.");
            return new HashMap<>();
        }
    }

    private static void writeToFile(File file, Map<String, Boolean> map) {
        try {
            if (file.exists()) file.delete();
            file.createNewFile();

            final OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            for (String line : map.keySet()) writer.write(line + "\n");
            writer.close();
        }

        catch (IOException ex) {
            Log.e(TAG, "Unable to write to file", ex);
        }
    }

    private static Map<String, Boolean> readFromFile(File file, boolean boolValue) {
        try {
            final BufferedReader reader = new BufferedReader(new FileReader(file));
            final Map<String, Boolean> filterMap = new HashMap<>();

            String line;
            while ((line = reader.readLine()) != null) {
                filterMap.put(line, boolValue);
            }
            reader.close();

            return filterMap;
        }

        catch (IOException ex) {
            Log.e(TAG, "Unable to read from file.", ex);
            return new HashMap<>();
        }
    }
}
