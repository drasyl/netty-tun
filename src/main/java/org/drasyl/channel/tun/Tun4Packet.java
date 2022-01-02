package org.drasyl.channel.tun;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.StringUtil;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * <a href="https://datatracker.ietf.org/doc/html/rfc791#section-3.1">RFC 791</a>
 */
public class Tun4Packet extends TunPacket {
    public static final int IP4_INTERNET_HEADER_LENGTH = 0;
    public static final int IP4_TOTAL_LENGTH = 2;
    public static final int IP4_PROTOCOL = 9;
    public static final int IP4_SOURCE_ADDRESS = 12;
    public static final int IP4_SOURCE_ADDRESS_LENGTH = 4;
    public static final int IP4_DESTINATION_ADDRESS = 16;
    public static final int IP4_DESTINATION_ADDRESS_LENGTH = 4;
    private InetAddress sourceAddress;
    private InetAddress destinationAddress;

    public Tun4Packet(final ByteBuf data) {
        super(data);
    }

    @SuppressWarnings("java:S109")
    @Override
    public int version() {
        return 4;
    }

    public int internetHeaderLength() {
        return content().getUnsignedByte(IP4_INTERNET_HEADER_LENGTH) & 0x0f;
    }

    public int totalLength() {
        return content().getUnsignedShort(IP4_TOTAL_LENGTH);
    }

    public int protocol() {
        return content().getUnsignedByte(IP4_PROTOCOL);
    }

    @SuppressWarnings("java:S1166")
    @Override
    public InetAddress sourceAddress() {
        if (sourceAddress == null) {
            try {
                byte[] dst = new byte[IP4_DESTINATION_ADDRESS_LENGTH];
                content().getBytes(IP4_SOURCE_ADDRESS, dst, 0, IP4_SOURCE_ADDRESS_LENGTH);
                sourceAddress = InetAddress.getByAddress(dst);
            }
            catch (final UnknownHostException e) {
                // unreachable code
                throw new IllegalStateException();
            }
        }
        return sourceAddress;
    }

    @SuppressWarnings("java:S1166")
    @Override
    public InetAddress destinationAddress() {
        if (destinationAddress == null) {
            try {
                byte[] dst = new byte[IP4_DESTINATION_ADDRESS_LENGTH];
                content().getBytes(IP4_DESTINATION_ADDRESS, dst, 0, IP4_DESTINATION_ADDRESS_LENGTH);
                destinationAddress = InetAddress.getByAddress(dst);
            }
            catch (final UnknownHostException e) {
                // unreachable code
                throw new IllegalStateException();
            }
        }
        return destinationAddress;
    }

    @SuppressWarnings("StringBufferReplaceableByString")
    @Override
    public String toString() {
        return new StringBuilder(StringUtil.simpleClassName(this))
                .append('[')
                .append("src=").append(sourceAddress())
                .append(", dst=").append(destinationAddress())
                .append(", len=").append(totalLength())
                .append(", content=").append(content())
                .append(']').toString();
    }
}
