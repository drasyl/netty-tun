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
import io.netty.channel.socket.Tun6Packet;
import io.netty.channel.socket.TunPacket;

import java.net.InetAddress;

import static io.netty.channel.socket.Tun6Packet.INET6_DESTINATION_ADDRESS;
import static io.netty.channel.socket.Tun6Packet.INET6_SOURCE_ADDRESS;

/**
 * Replies to IPv6-ICMP echo ping requests.
 */
@Sharable
public class Ping6Handler extends SimpleChannelInboundHandler<Tun6Packet> {
    // https://www.iana.org/assignments/protocol-numbers/protocol-numbers.xhtml
    public static final int PROTOCOL = 58;
    // https://datatracker.ietf.org/doc/html/rfc8200
    public static final int NEXT_HEADER = 6;
    public static final int TYPE = 40;
    public static final int CHECKSUM = 42;
    public static final int ECHO = 128;
    public static final int ECHO_REPLY = 129;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx,
                                Tun6Packet packet) {
        int nextHeader = packet.content().getUnsignedByte(NEXT_HEADER);
        if (nextHeader == PROTOCOL) {
            short icmpType = packet.content().getUnsignedByte(TYPE);
            if (icmpType == ECHO) {
                InetAddress source = packet.sourceAddress();
                InetAddress destination = packet.destinationAddress();
                int checksum = packet.content().getUnsignedShort(CHECKSUM);

                // create response
                ByteBuf buf = packet.content();
                buf.setBytes(INET6_SOURCE_ADDRESS, destination.getAddress());
                buf.setBytes(INET6_DESTINATION_ADDRESS, source.getAddress());
                buf.setByte(TYPE, ECHO_REPLY);
                buf.setShort(CHECKSUM, checksum - 0x100);

                TunPacket response = new Tun6Packet(buf.retain());
                ctx.writeAndFlush(response);
            }
        }
    }
}
