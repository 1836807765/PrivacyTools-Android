package com.kimbr.privacytools.internal;

public class Preferences {

    // Setup SharedPreferences store here

    public static boolean isLoggingEnabled() {
        return false;
    }

    public static boolean isFilteringEnabled() {
        return false;
    }

    public static boolean useHostsFile() {
        return false;
    }

    public static String getHostsUrl() {
        return "https://raw.githubusercontent.com/grufwub/DNS-Blocklist-Compiler/master/hosts";
    }
}
