package com.kimbr.privacytools.internal.vpn.network;

import com.kimbr.privacytools.internal.vpn.network.headers.IP4Header;
import com.kimbr.privacytools.internal.vpn.network.headers.TCPHeader;
import com.kimbr.privacytools.internal.vpn.network.headers.UDPHeader;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class Packet {

    public static final int IP4_HEADER_SIZE = 20;
    public static final int TCP_HEADER_SIZE = 20;
    public static final int UDP_HEADER_SIZE = 8;

    public IP4Header ip4Header;
    public TCPHeader tcpHeader;
    public UDPHeader udpHeader;
    public ByteBuffer backingBuffer;

    private boolean isUdp = false;
    private boolean isTcp = false;

    public Packet(ByteBuffer buffer) throws UnknownHostException {
        this.ip4Header = new IP4Header(buffer);

        if (ip4Header.protocol == IP4Header.TransportProtocol.TCP) {
            this.tcpHeader = new TCPHeader(buffer);
            this.isTcp = true;
        }

        else if (ip4Header.protocol == IP4Header.TransportProtocol.UDP) {
            this.udpHeader = new UDPHeader(buffer);
            this.isUdp = true;
        }

        this.backingBuffer = buffer;
    }

    public boolean isUdp() {
        return isUdp;
    }

    public boolean isTcp() {
        return isTcp;
    }

    public void swapSourceAndDestination() {
        // Swaps destination and source address
        final InetAddress newSourceAddress = ip4Header.destinationAddress;
        ip4Header.destinationAddress = ip4Header.sourceAddress;
        ip4Header.sourceAddress = newSourceAddress;

        // Swaps destination and source ports
        final int newSourcePort;
        if (isUdp) {
            newSourcePort = udpHeader.destinationPort;
            udpHeader.destinationPort = udpHeader.sourcePort;
            udpHeader.sourcePort = newSourcePort;
        }

        else if (isTcp) {
            newSourcePort = tcpHeader.destinationPort;
            tcpHeader.destinationPort = tcpHeader.sourcePort;
            tcpHeader.sourcePort = newSourcePort;
        }
    }

    public void updateTcpBuffer(ByteBuffer buffer, byte flags, long sequenceNumber, long acknowledgementNumber, int payloadSize) {
        buffer.position(0);
        fillHeader(buffer);
        backingBuffer = buffer;

        tcpHeader.flags = flags;
        backingBuffer.put(IP4_HEADER_SIZE + 13, flags);

        tcpHeader.sequenceNumber = sequenceNumber;
        backingBuffer.putInt(IP4_HEADER_SIZE + 4, (int) sequenceNumber);

        tcpHeader.acknowledgementNumber = acknowledgementNumber;
        backingBuffer.putInt(IP4_HEADER_SIZE + 8, (int) acknowledgementNumber);

        // Reset header size, since we don't need options
        final byte dataOffset = (byte) (TCP_HEADER_SIZE << 2);
        tcpHeader.dataOffsetAndReserved = dataOffset;
        backingBuffer.put(IP4_HEADER_SIZE + 12, dataOffset);

        updateTcpChecksum(payloadSize);

        final int ip4TotalLength = IP4_HEADER_SIZE + TCP_HEADER_SIZE + payloadSize;
        backingBuffer.putShort(2, (short) ip4TotalLength);
        ip4Header.totalLength = ip4TotalLength;

        updateIp4Checksum();
    }

    public void updateUdpBuffer(ByteBuffer buffer, int payloadSize) {
        buffer.position(0);
        fillHeader(buffer);
        backingBuffer = buffer;

        final int udpTotalLength = UDP_HEADER_SIZE + payloadSize;
        backingBuffer.putShort(IP4_HEADER_SIZE + 4, (short) udpTotalLength);
        udpHeader.length = udpTotalLength;

        // disable udp checksum verification
        backingBuffer.putShort(IP4_HEADER_SIZE + 6, (short) 0);
        udpHeader.checksum = 0;

        final int ip4TotalLength = IP4_HEADER_SIZE + udpTotalLength;
        backingBuffer.putShort(2, (short) ip4TotalLength);
        ip4Header.totalLength = ip4TotalLength;

        updateIp4Checksum();
    }

    private void updateIp4Checksum() {
        final ByteBuffer buffer = backingBuffer.duplicate();
        buffer.position(0);

        // Clear previous checksum
        buffer.putShort(10, (short) 0);

        int ipLength = ip4Header.headerLength;
        int sum = 0;

        while (ipLength > 0) {
            sum += BitUtils.getUnsignedShort(buffer.getShort());
            ipLength -= 2;
        }

        while (sum >> 16 > 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }

        sum = ~sum;
        ip4Header.headerChecksum = sum;
        backingBuffer.putShort(10, (short) sum);
    }

    private void updateTcpChecksum(int payloadSize) {
        int sum = 0;
        int tcpLength = TCP_HEADER_SIZE + payloadSize;

        // Calculate the pseudo-header checksum
        ByteBuffer buffer = ByteBuffer.wrap(ip4Header.sourceAddress.getAddress());
        sum = BitUtils.getUnsignedShort(buffer.getShort()) + BitUtils.getUnsignedShort(buffer.getShort());

        buffer = ByteBuffer.wrap(ip4Header.destinationAddress.getAddress());
        sum += BitUtils.getUnsignedShort(buffer.getShort()) + BitUtils.getUnsignedShort(buffer.getShort());
        sum += IP4Header.TransportProtocol.TCP.getNumber() + tcpLength;

        buffer = backingBuffer.duplicate();

        // clear previous checksum
        buffer.putShort(IP4_HEADER_SIZE + 16, (short) 0);

        // Calculate TCP segment checksum
        buffer.position(IP4_HEADER_SIZE);
        while (tcpLength > 1) {
            sum += BitUtils.getUnsignedShort(buffer.getShort());
            tcpLength -= 2;
        }

        if (tcpLength > 0)
            sum += BitUtils.getUnsignedByte(buffer.get()) << 8;

        while (sum >> 16 > 0)
            sum = (sum & 0xFFFF) + (sum >> 16);

        sum = ~sum;
        tcpHeader.checksum = sum;
        backingBuffer.putShort(IP4_HEADER_SIZE + 16, (short) sum);
    }

    private void fillHeader(ByteBuffer buffer) {
        ip4Header.fillHeader(buffer);

        if (isUdp) udpHeader.fillHeader(buffer);
        else if (isTcp) tcpHeader.fillHeader(buffer);
    }

    @Override
    public String toString() {
        final StringBuilder stringBuilder = new StringBuilder("Packet{");
        stringBuilder.append("ip4header=").append(ip4Header);
        stringBuilder.append(", ");

        if (isTcp) stringBuilder.append("tcpHeader=").append(tcpHeader);
        else if (isUdp) stringBuilder.append("udpHeader=").append(udpHeader);

        stringBuilder.append(", ");
        stringBuilder.append(backingBuffer.limit() - backingBuffer.position());
        stringBuilder.append("}");
        return stringBuilder.toString();
    }
}
