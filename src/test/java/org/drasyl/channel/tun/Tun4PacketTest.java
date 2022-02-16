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
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.drasyl.channel.tun.Tun4Packet.INET4_FLAGS_DONT_FRAGMENT_MASK;
import static org.drasyl.channel.tun.Tun4Packet.INET4_FLAGS_MORE_FRAGMENTS_MASK;
import static org.drasyl.channel.tun.Tun4Packet.INET4_TYPE_OF_SERVICE_DELAY_MASK;
import static org.drasyl.channel.tun.Tun4Packet.INET4_TYPE_OF_SERVICE_PRECEDENCE_ROUTINE;
import static org.drasyl.channel.tun.Tun4Packet.INET4_TYPE_OF_SERVICE_RELIBILITY_MASK;
import static org.drasyl.channel.tun.Tun4Packet.INET4_TYPE_OF_SERVICE_THROUGHPUT_MASK;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Tun4PacketTest {
    private Tun4Packet packet;

    @BeforeEach
    void setUp() {
        ByteBuf data = Unpooled.wrappedBuffer(new byte[]{
                69,
                0,
                0,
                62,
                -16,
                125,
                64,
                0,
                1,
                17,
                6,
                1,
                10,
                -31,
                -41,
                84,
                -32,
                0,
                0,
                -5,
                20,
                -23,
                20,
                -23,
                0,
                42,
                -107,
                122,
                0,
                0,
                0,
                0,
                0,
                1,
                0,
                0,
                0,
                0,
                0,
                0,
                10,
                114,
                109,
                119,
                121,
                122,
                100,
                105,
                118,
                117,
                117,
                5,
                108,
                111,
                99,
                97,
                108,
                0,
                0,
                1,
                0,
                1
        });
        packet = new Tun4Packet(data);
    }

    @Test
    void testConstructor() {
        final ByteBuf buf = packet.content().readBytes(19);
        assertThrows(IllegalArgumentException.class, () -> new Tun4Packet(buf));
    }

    @Test
    void testVersion() {
        assertEquals(4, packet.version());
    }

    @Test
    void testInternetHeaderLength() {
        assertEquals(5, packet.internetHeaderLength());
    }

    @Test
    void testTypeOfService() {
        assertEquals(0, packet.typeOfService());
        assertEquals(INET4_TYPE_OF_SERVICE_PRECEDENCE_ROUTINE, packet.typeOfService() >> 5);
        assertFalse((packet.typeOfService() & INET4_TYPE_OF_SERVICE_DELAY_MASK) > 0);
        assertFalse((packet.typeOfService() & INET4_TYPE_OF_SERVICE_THROUGHPUT_MASK) > 0);
        assertFalse((packet.typeOfService() & INET4_TYPE_OF_SERVICE_RELIBILITY_MASK) > 0);
    }

    @Test
    void testTotalLength() {
        assertEquals(62, packet.totalLength());
    }

    @Test
    void testIdentification() {
        assertEquals(61565, packet.identification());
    }

    @Test
    void testFlags() {
        assertEquals(2, packet.flags());
        assertTrue((packet.flags() & INET4_FLAGS_DONT_FRAGMENT_MASK) > 0);
        assertFalse((packet.flags() & INET4_FLAGS_MORE_FRAGMENTS_MASK) > 0);
    }

    @Test
    void testFragmentOffset() {
        assertEquals(0, packet.fragmentOffset());
    }

    @Test
    void testTimeToLive() {
        assertEquals(1, packet.timeToLive());
    }

    @Test
    void testProtocol() {
        assertEquals(17, packet.protocol());
    }

    @Test
    void testHeaderChecksum() {
        assertEquals(1537, packet.headerChecksum());
    }

    @Test
    void testSourceAddress() throws UnknownHostException {
        assertEquals(InetAddress.getByName("10.225.215.84"), packet.sourceAddress());
    }

    @Test
    void testDestinationAddress() throws UnknownHostException {
        assertEquals(InetAddress.getByName("224.0.0.251"), packet.destinationAddress());
    }

    @Test
    void testData() {
        assertArrayEquals(new byte[]{
                20,
                -23,
                20,
                -23,
                0,
                42,
                -107,
                122,
                0,
                0,
                0,
                0,
                0,
                1,
                0,
                0,
                0,
                0,
                0,
                0,
                10,
                114,
                109,
                119,
                121,
                122,
                100,
                105,
                118,
                117,
                117,
                5,
                108,
                111,
                99,
                97,
                108,
                0,
                0,
                1,
                0,
                1
        }, packet.data());
    }

    @Test
    void testToString() {
        assertEquals("Tun4Packet[id=61565, len=62, src=10.225.215.84, dst=224.0.0.251]", packet.toString());
    }

    @Test
    void verifyChecksum() {
        ByteBuf buf = Unpooled.wrappedBuffer(new byte[]{
                69, 0, 0, 115, 0, 0, 64, 0, 64, 17, -72, 97, -64, -88, 0, 1, -64, -88, 0, -57
        });

        final Tun4Packet tun4Packet = new Tun4Packet(buf);
        assertTrue(tun4Packet.verifyChecksum());
    }

    @Test
    void calculateChecksum() {
        // from https://en.wikipedia.org/wiki/IPv4_header_checksum#Calculating_the_IPv4_header_checksum
        ByteBuf buf = Unpooled.wrappedBuffer(new byte[]{
                69, 0, 0, 115, 0, 0, 64, 0, 64, 17, 0, 0, -64, -88, 0, 1, -64, -88, 0, -57
        });

        assertEquals(47201, Tun4Packet.calculateChecksum(buf));
    }

    @Test
    void populatePacket() throws UnknownHostException {
        final int typeOfService = 0;
        final int identification = 61565;
        final int flags = 2;
        final int fragmentOffset = 0;
        final int timeToLive = 64;
        final InetProtocol protocol = InetProtocol.UDP;
        final boolean calculateChecksum = true;
        final Inet4Address sourceAddress = (Inet4Address) InetAddress.getByName("123.234.123.234");
        final Inet4Address destinationAddress = (Inet4Address) InetAddress.getByName("234.123.234.123");
        // udp
        final byte[] data = {
                1, 2, 3, 4,
                0, 8, 0, 0
        };

        final ByteBuf buffer = Unpooled.buffer();
        try {
            Tun4Packet.populatePacket(buffer, typeOfService, identification, flags, fragmentOffset, timeToLive, protocol, calculateChecksum, sourceAddress, destinationAddress, data);
            Tun4Packet packet = new Tun4Packet(buffer);

            assertEquals(typeOfService, packet.typeOfService());
            assertEquals(identification, packet.identification());
            assertEquals(flags, packet.flags());
            assertEquals(fragmentOffset, packet.fragmentOffset());
            assertEquals(timeToLive, packet.timeToLive());
            assertEquals(timeToLive, packet.timeToLive());
            assertEquals(protocol.decimal, packet.protocol());
            assertEquals(sourceAddress, packet.sourceAddress());
            assertEquals(destinationAddress, packet.destinationAddress());
            assertArrayEquals(data, packet.data());
            assertTrue(packet.verifyChecksum());
        }
        finally {
            buffer.release();
        }
    }
}
