package org.drasyl.channel.tun.jna;

import io.netty.buffer.ByteBufAllocator;
import org.drasyl.channel.tun.TunPacket;

import java.io.Closeable;
import java.io.IOException;

/**
 * Defines a TUN device we can read from/write to.
 */
public interface TunDevice extends Closeable {
    /**
     * The actual name of the device.
     *
     * @return The actual name of the device.
     */
    String name();

    /**
     * Reads and blocks until a {@link TunPacket} has been received by the tun device.
     *
     * @param alloc
     * @return {@link TunPacket} received by the tun device
     * @throws IOException if read failed
     */
    TunPacket readPacket(final ByteBufAllocator alloc) throws IOException;

    /**
     * Writes and blocks until a {@link TunPacket} has been sent by the tun device.
     *
     * @param alloc
     * @param msg   {@link TunPacket} to write by the tun device
     * @throws IOException if write failed
     */
    void writePacket(final ByteBufAllocator alloc, final TunPacket msg) throws IOException;
}
