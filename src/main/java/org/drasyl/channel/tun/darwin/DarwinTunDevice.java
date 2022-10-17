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
package org.drasyl.channel.tun.darwin;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.drasyl.channel.tun.Tun4Packet;
import org.drasyl.channel.tun.Tun6Packet;
import org.drasyl.channel.tun.TunAddress;
import org.drasyl.channel.tun.TunPacket;
import org.drasyl.channel.tun.jna.AbstractTunDevice;
import org.drasyl.channel.tun.jna.TunDevice;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.drasyl.channel.tun.jna.shared.Socket.ADDRESS_FAMILY_SIZE;
import static org.drasyl.channel.tun.jna.shared.Socket.AF_INET;

/**
 * {@link TunDevice} implementation for Darwin-based platforms.
 */
public final class DarwinTunDevice extends AbstractTunDevice {
    private static final String DEVICE_PREFIX = "utun";
    private static final IllegalArgumentException ILLEGAL_NAME_EXCEPTION = new IllegalArgumentException("Device name must be 'utun<index>' or null.");
    private static final ByteBuf ADDRESS_FAMILY_BUF = Unpooled.unreleasableBuffer(Unpooled.wrappedBuffer(new byte[]{
            (byte) (AF_INET >> 24), (byte) (AF_INET >> 16), (byte) (AF_INET >> 8), (byte) AF_INET
    }));
    private final int fd;
    private final int readBytes;

    private DarwinTunDevice(final int fd, final int mtu, final TunAddress localAddress) {
        super(localAddress);
        this.fd = fd;
        this.readBytes = mtu + ADDRESS_FAMILY_SIZE;
    }

    public static TunDevice open(final String name, int mtu) throws IOException {
        final int index;
        if (name != null) {
            if (name.startsWith(DEVICE_PREFIX)) {
                try {
                    index = Integer.parseInt(name.substring(DEVICE_PREFIX.length()));
                }
                catch (final NumberFormatException e) {
                    throw ILLEGAL_NAME_EXCEPTION;
                }
            }
            else {
                throw ILLEGAL_NAME_EXCEPTION;
            }
        }
        else {
            index = 0;
        }

        final DarwinTunDevice device = Native.open(index, mtu);

        if (device == null) {
            throw new IOException("Create an endpoint for communication failed.");
        }

        return device;
    }

    @Override
    public TunPacket readPacket(final ByteBufAllocator alloc) throws IOException {
        if (closed) {
            throw new IOException("Device is closed.");
        }

        // read from socket
        final ByteBuf maxByteBuf = alloc.buffer(readBytes).writerIndex(readBytes);
        final int bytesRead;
        if (maxByteBuf.hasMemoryAddress()) {
            // has a memory address so use optimized call
            bytesRead = Native.readAddress(fd, maxByteBuf.memoryAddress(), maxByteBuf.readerIndex(), maxByteBuf.capacity());
        }
        else {
            final ByteBuffer byteBuffer = maxByteBuf.internalNioBuffer(0, maxByteBuf.readableBytes());
            bytesRead = Native.read(fd, byteBuffer, byteBuffer.position(), byteBuffer.limit());
        }

        // shrink bytebuf to actual required size
        final ByteBuf actualByteBuf = maxByteBuf
                .setIndex(ADDRESS_FAMILY_SIZE, bytesRead) // skip address family header
                .capacity(bytesRead)
                .slice();

        // extract ip version
        final int version = Byte.toUnsignedInt(actualByteBuf.getByte(0)) >> 4;

        if (version == 4) {
            return new Tun4Packet(actualByteBuf);
        }
        else if (version == 6) {
            return new Tun6Packet(actualByteBuf);
        }
        else {
            throw new IOException("Unknown protocol: " + version);
        }
    }

    @Override
    public void writePacket(final ByteBufAllocator alloc, final TunPacket msg) throws IOException {
        if (closed) {
            throw new IOException("Device is closed.");
        }

        if (msg instanceof Tun4Packet) {
            // add address family
            final ByteBuf byteBuf = alloc.directBuffer(ADDRESS_FAMILY_SIZE + msg.content().readableBytes(), ADDRESS_FAMILY_SIZE + msg.content().readableBytes());
            ADDRESS_FAMILY_BUF.resetReaderIndex();
            byteBuf.writeBytes(ADDRESS_FAMILY_BUF);
            byteBuf.writeBytes(msg.content());
            msg.release();

            if (byteBuf.hasMemoryAddress()) {
                Native.writeAddress(fd, byteBuf.memoryAddress(), byteBuf.readerIndex(), byteBuf.writerIndex());
            }
            else {
                final ByteBuffer byteBuffer = byteBuf.internalNioBuffer(0, byteBuf.readableBytes());
                Native.write(fd, byteBuffer, byteBuffer.position(), byteBuffer.limit());
            }

            byteBuf.release();
        }
        else {
            throw new IOException("Unknown address family: " + msg.getClass().getSimpleName());
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;

            // close tun device
            Native.close(fd);
        }
    }
}
