package com.kimbr.privacytools.internal.vpn.network.headers;

import com.kimbr.privacytools.internal.vpn.network.BitUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class IP4Header {

    public byte version;
    public byte ihl;
    public int headerLength;
    public short typeOfService;
    public int totalLength;
    public int identificationAndFlagsAndFragmentOffset;

    public short ttl;
    private short protocolNumber;
    public TransportProtocol protocol;
    public int headerChecksum;

    public InetAddress sourceAddress;
    public InetAddress destinationAddress;

    public int optionsAndPadding;

    public IP4Header(ByteBuffer buffer) throws UnknownHostException {
        final byte versionAndIhl = buffer.get();
        this.version = (byte) (versionAndIhl >> 4);
        this.ihl = (byte) (versionAndIhl & 0x0F);
        this.headerLength = ihl << 2;

        this.typeOfService = BitUtils.getUnsignedByte(buffer.get());
        this.totalLength = BitUtils.getUnsignedShort(buffer.getShort());
        this.identificationAndFlagsAndFragmentOffset = buffer.getInt();

        this.ttl = BitUtils.getUnsignedByte(buffer.get());
        this.protocolNumber = BitUtils.getUnsignedByte(buffer.get());
        this.protocol = TransportProtocol.numberToEnum(protocolNumber);
        this.headerChecksum = BitUtils.getUnsignedShort(buffer.getShort());

        byte[] addressBytes = new byte[4];
        buffer.get(addressBytes, 0, 4);
        this.sourceAddress = InetAddress.getByAddress(addressBytes);
        buffer.get(addressBytes, 0, 4);
        this.destinationAddress = InetAddress.getByAddress(addressBytes);

        this.optionsAndPadding = buffer.getInt();
    }

    public void fillHeader(ByteBuffer buffer) {
        buffer.put((byte) (version << 4 | ihl));
        buffer.put((byte) typeOfService);
        buffer.putShort((short) totalLength);
        buffer.putInt(identificationAndFlagsAndFragmentOffset);
        buffer.put((byte) ttl);
        buffer.put((byte) protocol.getNumber());
        buffer.putShort((short) headerChecksum);
        buffer.put(sourceAddress.getAddress());
        buffer.put(destinationAddress.getAddress());
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder("IP4Header{");
        builder.append("version=").append(version).append(", ");
        builder.append("IHL=").append(ihl).append(", ");
        builder.append("typeOfService=").append(typeOfService).append(", ");
        builder.append("totalLength=").append(totalLength).append(", ");
        builder.append("identificationAndFlagsAndFragmentOffset=").append(identificationAndFlagsAndFragmentOffset).append(", ");
        builder.append("TTL=").append(ttl).append(", ");
        builder.append("protocol=").append(protocolNumber).append(protocol).append(", ");
        builder.append("headerChecksum=").append(headerChecksum).append(", ");
        builder.append("sourceAddress=").append(sourceAddress.getHostAddress()).append(", ");
        builder.append("destinationAddress=").append(destinationAddress.getHostAddress()).append("}");
        return builder.toString();
    }

    public enum TransportProtocol {
        TCP(6),
        UDP(17),
        Other(0xFF);

        private int protocolNumber;

        TransportProtocol(int protocolNumber) {
            this.protocolNumber = protocolNumber;
        }

        public static TransportProtocol numberToEnum(int protocolNumber) {
            switch (protocolNumber) {
                case 6:
                    return TCP;

                case 17:
                    return UDP;

                default:
                    return Other;
            }
        }

        public int getNumber() {
            return protocolNumber;
        }
    }
}
