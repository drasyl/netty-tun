package org.drasyl.channel.tun;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.StringUtil;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * <a href="https://datatracker.ietf.org/doc/html/rfc2460#section-3">RFC 2460</a>
 */
public class Tun6Packet extends TunPacket {
    public static final int IP6_NEXT_HEADER = 6;
    public static final int IP6_SOURCE_ADDRESS = 8;
    public static final int IP6_SOURCE_ADDRESS_LENGTH = 16;
    public static final int IP6_DESTINATION_ADDRESS = 24;
    public static final int IP6_DESTINATION_ADDRESS_LENGTH = 16;
    private InetAddress sourceAddress;
    private InetAddress destinationAddress;

    public Tun6Packet(final ByteBuf data) {
        super(data);
    }

    @SuppressWarnings("java:S109")
    @Override
    public int version() {
        return 6;
    }

    public int nextHeader() {
        return content().getUnsignedByte(IP6_NEXT_HEADER);
    }

    @SuppressWarnings("java:S1166")
    @Override
    public InetAddress sourceAddress() {
        if (sourceAddress == null) {
            try {
                byte[] dst = new byte[IP6_SOURCE_ADDRESS_LENGTH];
                content().getBytes(IP6_SOURCE_ADDRESS, dst, 0, IP6_SOURCE_ADDRESS_LENGTH);
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
                byte[] dst = new byte[IP6_SOURCE_ADDRESS_LENGTH];
                content().getBytes(IP6_DESTINATION_ADDRESS, dst, 0, IP6_DESTINATION_ADDRESS_LENGTH);
                destinationAddress = InetAddress.getByAddress(dst);
            }
            catch (final UnknownHostException e) {
                // unreachable code
                throw new IllegalStateException();
            }
        }
        return destinationAddress;
    }

    @Override
    public String toString() {
        return new StringBuilder(StringUtil.simpleClassName(this))
                .append('[')
                .append("src=").append(sourceAddress())
                .append(", dst=").append(destinationAddress())
                .append(", content=").append(content())
                .append(']').toString();
    }
}
