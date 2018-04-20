package com.kimbr.privacytools.internal.vpn.network.headers;

import com.kimbr.privacytools.internal.vpn.network.BitUtils;

import java.nio.ByteBuffer;

import static com.kimbr.privacytools.internal.vpn.network.Packet.TCP_HEADER_SIZE;

public class TCPHeader extends AbstractHeader {

    public static final int FIN = 0x01;
    public static final int SYN = 0x02;
    public static final int RST = 0x04;
    public static final int PSH = 0x08;
    public static final int ACK = 0x10;
    public static final int URG = 0x20;

    public long sequenceNumber;
    public long acknowledgementNumber;

    public byte dataOffsetAndReserved;
    public int headerLength;
    public byte flags;
    public int window;

    public int checksum;
    public int urgentPointer;
    public byte[] optionsAndPadding;

    public TCPHeader(ByteBuffer buffer) {
        super(buffer);

        this.sequenceNumber = BitUtils.getUnsignedInt(buffer.getInt());
        this.acknowledgementNumber = BitUtils.getUnsignedInt(buffer.getInt());
        this.dataOffsetAndReserved = buffer.get();
        this.headerLength = (dataOffsetAndReserved & 0x0F) >> 2;
        this.flags = buffer.get();
        this.window = BitUtils.getUnsignedShort(buffer.getShort());
        this.checksum = BitUtils.getUnsignedShort(buffer.getShort());
        this.urgentPointer = BitUtils.getUnsignedShort(buffer.getShort());

        final int optionsLength = headerLength - TCP_HEADER_SIZE;
        if (optionsLength > 0) {
            this.optionsAndPadding = new byte[optionsLength];
            buffer.get(optionsAndPadding, 0, optionsLength);
        }
    }

    public boolean isFIN() {
        return (flags & FIN) == FIN;
    }

    public boolean isSYN() {
        return (flags & SYN) == SYN;
    }

    public boolean isRST() {
        return (flags & RST) == RST;
    }

    public boolean isPSH() {
        return (flags & PSH) == PSH;
    }

    public boolean isACK() {
        return (flags & ACK) == ACK;
    }

    public boolean isURG() {
        return (flags & URG) == URG;
    }

    @Override
    public void fillHeader(ByteBuffer buffer) {
        super.fillHeader(buffer);

        buffer.putInt((int) sequenceNumber);
        buffer.putInt((int) acknowledgementNumber);
        buffer.put(dataOffsetAndReserved);
        buffer.put(flags);
        buffer.putShort((short) window);
        buffer.putShort((short) checksum);
        buffer.putShort((short) urgentPointer);
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder("TCPHeader{");
        builder.append("sourcePort=").append(sourcePort).append(", ");
        builder.append("destinationPort=").append(destinationPort).append(", ");
        builder.append("sequenceNumber=").append(sequenceNumber).append(", ");
        builder.append("acknowledgementNumber=").append(acknowledgementNumber).append(", ");
        builder.append("headerLength=").append(headerLength).append(", ");
        builder.append("window=").append(window).append(", ");
        builder.append("checksum=").append(checksum).append(", ");
        builder.append("flags=");

        if (isFIN()) builder.append(" FIN");
        else if (isSYN()) builder.append(" SYN");
        else if (isRST()) builder.append(" RST");
        else if (isPSH()) builder.append(" PSH");
        else if (isACK()) builder.append(" ACK");
        else if (isURG()) builder.append(" URG");
        builder.append("}");

        return builder.toString();
    }
}
