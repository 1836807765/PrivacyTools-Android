package com.kimbr.privacytools.internal.vpn.network.headers;

import com.kimbr.privacytools.internal.vpn.network.BitUtils;

import java.nio.ByteBuffer;

public abstract class AbstractHeader {

    public int sourcePort;
    public int destinationPort;

    AbstractHeader(ByteBuffer buffer) {
        this.sourcePort = BitUtils.getUnsignedShort(buffer.getShort());
        this.destinationPort = BitUtils.getUnsignedShort(buffer.getShort());
    }

    public void fillHeader(ByteBuffer buffer) {
        buffer.putShort((short) sourcePort);
        buffer.putShort((short) destinationPort);
    }
}
