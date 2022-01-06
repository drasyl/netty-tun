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

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Tun6PacketTest {
    private Tun6Packet packet;

    @BeforeEach
    void setUp() {
        ByteBuf data = Unpooled.wrappedBuffer(new byte[]{
                96,
                38,
                12,
                0,
                0,
                117,
                6,
                64,
                -2,
                -128,
                0,
                0,
                0,
                0,
                0,
                0,
                28,
                -33,
                23,
                75,
                -111,
                -33,
                100,
                7,
                -2,
                -128,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                102,
                68,
                94,
                -66,
                -33,
                -8,
                67,
                -61,
                -126,
                27,
                88,
                -28,
                2,
                55,
                90,
                -113,
                -37,
                113,
                -65,
                -128,
                24,
                8,
                0,
                -66,
                -37,
                0,
                0,
                1,
                1,
                8,
                10,
                23,
                -117,
                42,
                80,
                -34,
                37,
                8,
                -13
        });
        packet = new Tun6Packet(data);
    }

    @Test
    void testVersion() {
        assertEquals(6, packet.version());
    }

    @Test
    void testTrafficClass() {
        assertEquals(2, packet.trafficClass());
    }

    @Test
    void testFlowLabel() {
        assertEquals(396288, packet.flowLabel());
    }

    @Test
    void testPayloadLength() {
        assertEquals(117, packet.payloadLength());
    }

    @Test
    void testNextHeader() {
        assertEquals(6, packet.nextHeader());
    }

    @Test
    void testHopLimit() {
        assertEquals(64, packet.hopLimit());
    }
    @Test
    void testSourceAddress() throws UnknownHostException {
        assertEquals(InetAddress.getByName("fe80:0:0:0:1cdf:174b:91df:6407"), packet.sourceAddress());
    }

    @Test
    void testDestinationAddress() throws UnknownHostException {
        assertEquals(InetAddress.getByName("fe80:0:0:0:66:445e:bedf:f843"), packet.destinationAddress());
    }

    @Test
    void testData() {
        assertArrayEquals(new byte[]{
                -61,
                -126,
                27,
                88,
                -28,
                2,
                55,
                90,
                -113,
                -37,
                113,
                -65,
                -128,
                24,
                8,
                0,
                -66,
                -37,
                0,
                0,
                1,
                1,
                8,
                10,
                23,
                -117,
                42,
                80,
                -34,
                37,
                8,
                -13
        }, packet.data());
    }

    @Test
    void testToString() {
        assertEquals("Tun6Packet[len=117, src=fe80:0:0:0:1cdf:174b:91df:6407, dst=fe80:0:0:0:66:445e:bedf:f843]", packet.toString());
    }
}
