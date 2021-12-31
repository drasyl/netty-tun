package org.drasyl.channel.tun;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
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
        try {
            return InetAddress.getByAddress(ByteBufUtil.getBytes(content(), IP6_SOURCE_ADDRESS, IP6_SOURCE_ADDRESS_LENGTH));
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
            return InetAddress.getByAddress(ByteBufUtil.getBytes(content(), IP6_DESTINATION_ADDRESS, IP6_DESTINATION_ADDRESS_LENGTH));
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
                .append(", content=").append(content())
                .append(']').toString();
    }
}
