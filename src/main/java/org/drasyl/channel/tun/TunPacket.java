package org.drasyl.channel.tun;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;

import java.net.InetAddress;

/**
 * Envelope class for IPv4 and IPv6 packets received from/sent to TUN devices.
 */
@SuppressWarnings("java:S118")
public abstract class TunPacket extends DefaultByteBufHolder {
    protected TunPacket(final ByteBuf data) {
        super(data);
    }

    /**
     * Returns the IP version.
     *
     * @return the IP version.
     */
    public abstract int version();

    /**
     * Returns the source address.
     *
     * @return the source address.
     */
    public abstract InetAddress sourceAddress();

    /**
     * Returns the destination address.
     *
     * @return the destination address.
     */
    public abstract InetAddress destinationAddress();
}
