package com.kimbr.privacytools.internal.vpn;

import com.kimbr.privacytools.internal.vpn.network.Packet;

public interface LoggingCallback {
    void log(Packet packet, Boolean filterResult);
}