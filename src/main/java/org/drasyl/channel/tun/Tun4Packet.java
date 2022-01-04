/*
 * Copyright (c) 2021-2022 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.channel.tun;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.StringUtil;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * IPv4-based {@link TunPacket}.
 */
public class Tun4Packet extends TunPacket {
    // https://datatracker.ietf.org/doc/html/rfc791#section-3.1
    public static final int IP4_INTERNET_HEADER_LENGTH = 0;
    public static final int IP4_TOTAL_LENGTH = 2;
    public static final int IP4_IDENTIFICATION = 4;
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

    public int identification() {
        return content().getUnsignedShort(IP4_IDENTIFICATION);
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
