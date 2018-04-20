package com.kimbr.privacytools.internal.vpn.network.headers;

import com.kimbr.privacytools.internal.vpn.network.BitUtils;

import java.nio.ByteBuffer;

public class UDPHeader extends AbstractHeader {

    public int length;
    public int checksum;

    public UDPHeader(ByteBuffer buffer) {
        super(buffer);

        this.length = BitUtils.getUnsignedShort(buffer.getShort());
        this.checksum = BitUtils.getUnsignedShort(buffer.getShort());
    }

    @Override
    public void fillHeader(ByteBuffer buffer) {
        super.fillHeader(buffer);

        buffer.putShort((short) length);
        buffer.putShort((short) checksum);
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder("UDPHeader{");
        builder.append("sourcePort=").append(sourcePort).append(", ");
        builder.append("destinationPort=").append(destinationPort).append(", ");
        builder.append("length=").append(length).append(", ");
        builder.append("checksum=").append(checksum).append("}");
        return builder.toString();
    }
}
