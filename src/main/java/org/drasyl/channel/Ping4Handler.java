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
package org.drasyl.channel;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.Tun4Packet;
import io.netty.channel.socket.TunPacket;

import java.net.InetAddress;

import static io.netty.channel.socket.Tun4Packet.INET4_DESTINATION_ADDRESS;
import static io.netty.channel.socket.Tun4Packet.INET4_SOURCE_ADDRESS;

/**
 * Replies to ICMP echo ping requests.
 */
@Sharable
public class Ping4Handler extends SimpleChannelInboundHandler<Tun4Packet> {
    // https://www.iana.org/assignments/protocol-numbers/protocol-numbers.xhtml
    public static final int PROTOCOL = 1;
    // https://datatracker.ietf.org/doc/html/rfc792
    public static final int TYPE = 20;
    public static final int CHECKSUM = 22;
    public static final int ECHO = 8;
    public static final int ECHO_REPLY = 0;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx,
                                Tun4Packet packet) {
        if (packet.protocol() == PROTOCOL) {
            short icmpType = packet.content().getUnsignedByte(TYPE);
            if (icmpType == ECHO) {
                System.out.println("PING READ  " + packet);
                InetAddress source = packet.sourceAddress();
                InetAddress destination = packet.destinationAddress();
                int checksum = packet.content().getUnsignedShort(CHECKSUM);

                // create response
                ByteBuf buf = packet.content();
                buf.setBytes(INET4_SOURCE_ADDRESS, destination.getAddress());
                buf.setBytes(INET4_DESTINATION_ADDRESS, source.getAddress());
                buf.setByte(TYPE, ECHO_REPLY);
                buf.setShort(CHECKSUM, (checksum + 0x0800) % 0xffff);

                TunPacket response = new Tun4Packet(buf.retain());
                System.out.println("PING WRITE " + response);
                ctx.writeAndFlush(response);
            }
        }
    }
}
