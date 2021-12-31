package org.drasyl.channel.tun;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
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

    public Tun4Packet(final ByteBuf data) {
        super(data);
    }

    @SuppressWarnings("java:S109")
    @Override
    public int version() {
        return 4;
    }

    public int internetHeaderLength() {
        return (int) content().getUnsignedByte(IP4_INTERNET_HEADER_LENGTH) & 0x0f;
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
        try {
            return InetAddress.getByAddress(ByteBufUtil.getBytes(content(), IP4_SOURCE_ADDRESS, IP4_SOURCE_ADDRESS_LENGTH));
        }
        catch (final UnknownHostException e) {
            // unreachable code
            throw new IllegalStateException();
        }
    }

    @SuppressWarnings("java:S1166")
    @Override
    public InetAddress destinationAddress() {
        try {
            return InetAddress.getByAddress(ByteBufUtil.getBytes(content(), IP4_DESTINATION_ADDRESS, IP4_DESTINATION_ADDRESS_LENGTH));
        }
        catch (final UnknownHostException e) {
            // unreachable code
            throw new IllegalStateException();
        }
    }

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
