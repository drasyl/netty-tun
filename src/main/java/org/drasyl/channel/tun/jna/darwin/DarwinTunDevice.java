package org.drasyl.channel.tun.jna.darwin;

import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Structure;
import com.sun.jna.Structure.FieldOrder;
import com.sun.jna.ptr.IntByReference;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import org.drasyl.channel.tun.Tun4Packet;
import org.drasyl.channel.tun.TunPacket;
import org.drasyl.channel.tun.jna.TunDevice;
import org.drasyl.channel.tun.jna.darwin.KernControl.CtlInfo;
import org.drasyl.channel.tun.jna.darwin.KernControl.SockaddrCtl;
import org.drasyl.channel.tun.jna.shared.If.Ifreq;
import org.drasyl.channel.tun.jna.shared.LibC;

import java.io.IOException;
import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;
import static org.drasyl.channel.tun.jna.darwin.IfUtun.UTUN_CONTROL_NAME;
import static org.drasyl.channel.tun.jna.darwin.IfUtun.UTUN_OPT_IFNAME;
import static org.drasyl.channel.tun.jna.darwin.Ioctl.SIOCGIFMTU;
import static org.drasyl.channel.tun.jna.darwin.KernControl.CTLIOCGINFO;
import static org.drasyl.channel.tun.jna.darwin.SysDomain.SYSPROTO_CONTROL;
import static org.drasyl.channel.tun.jna.shared.LibC.connect;
import static org.drasyl.channel.tun.jna.shared.LibC.getsockopt;
import static org.drasyl.channel.tun.jna.shared.LibC.ioctl;
import static org.drasyl.channel.tun.jna.shared.LibC.read;
import static org.drasyl.channel.tun.jna.shared.LibC.socket;
import static org.drasyl.channel.tun.jna.shared.LibC.write;
import static org.drasyl.channel.tun.jna.shared.Socket.ADDRESS_FAMILY_SIZE;
import static org.drasyl.channel.tun.jna.shared.Socket.AF_INET;
import static org.drasyl.channel.tun.jna.shared.Socket.AF_INET6;
import static org.drasyl.channel.tun.jna.shared.Socket.AF_SYSTEM;
import static org.drasyl.channel.tun.jna.shared.Socket.SOCK_DGRAM;

/**
 * {@link TunDevice} implementation for Darwin-based platforms.
 */
public final class DarwinTunDevice implements TunDevice {
    private static final String DEVICE_PREFIX = "utun";
    private static final IllegalArgumentException ILLEGAL_NAME_EXCEPTION = new IllegalArgumentException("Device name must be 'utun<index>' or null.");
    private static final ByteBuf ADDRESS_FAMILY_BUF = Unpooled.unreleasableBuffer(Unpooled.wrappedBuffer(new byte[]{
            (byte) (AF_INET >> 24), (byte) (AF_INET >> 16), (byte) (AF_INET >> 8), (byte) AF_INET
    }));
    private final int fd;
    private final NativeLong mtu;
    private final String name;
    private boolean closed;

    private DarwinTunDevice(final int fd, final int mtu, final String name) {
        this.fd = fd;
        this.mtu = new NativeLong(mtu);
        this.name = requireNonNull(name);
    }

    public static TunDevice open(final String name) throws IOException {
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

        // create socket
        final int fd = socket(AF_SYSTEM, SOCK_DGRAM, SYSPROTO_CONTROL);

        if (fd == -1) {
            throw new IOException("Create an endpoint for communication failed.");
        }

        // mark socket as utun device
        final CtlInfo ctlInfo = new CtlInfo(UTUN_CONTROL_NAME);
        ioctl(fd, CTLIOCGINFO, ctlInfo);

        // define address of socket
        final SockaddrCtl address = new SockaddrCtl(AF_SYSTEM, (short) SYSPROTO_CONTROL, ctlInfo.ctl_id, index);
        connect(fd, address, address.sc_len);

        // get socket name
        final SockName sockName = new SockName();
        final IntByReference sockNameLen = new IntByReference(SockName.LENGTH);
        getsockopt(fd, SYSPROTO_CONTROL, UTUN_OPT_IFNAME, sockName, sockNameLen);

        final String deviceName = Native.toString(sockName.name, US_ASCII);

        // get mtu
        final Ifreq ifreq = new Ifreq(deviceName);
        ioctl(fd, SIOCGIFMTU, ifreq);
        final int mtu = ifreq.ifr_ifru.ifru_mtu;

        return new DarwinTunDevice(fd, mtu, deviceName);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public TunPacket readPacket(final ByteBufAllocator alloc) throws IOException {
        if (closed) {
            throw new IOException("Device is closed.");
        }

        // read from socket
        final int capacity = ADDRESS_FAMILY_SIZE + mtu.intValue();
        final ByteBuf maxByteBuf = alloc.buffer(capacity).writerIndex(capacity);
        final ByteBuffer byteBuffer = maxByteBuf.nioBuffer();
        final int bytesRead = read(fd, byteBuffer, mtu);

        // extract address family
        final int addressFamily = maxByteBuf.getInt(0);

        // shrink bytebuf to actual required size
        final ByteBuf actualByteBuf = maxByteBuf
                .setIndex(ADDRESS_FAMILY_SIZE, bytesRead)
                .capacity(bytesRead)
                .slice();

        switch (addressFamily) {
            case AF_INET:
                return new Tun4Packet(actualByteBuf);

            case AF_INET6:
                return new Tun4Packet(actualByteBuf);

            default:
                throw new IOException("Unknown address family: " + addressFamily);
        }
    }

    @Override
    public void writePacket(final ByteBufAllocator alloc, final TunPacket msg) throws IOException {
        if (closed) {
            throw new IOException("Device is closed.");
        }

        if (msg instanceof Tun4Packet) {
            // add address family
            final CompositeByteBuf byteBuf = alloc.compositeBuffer(2).addComponents(true, ADDRESS_FAMILY_BUF, msg.content());
            final ByteBuffer byteBuffer = byteBuf.nioBuffer();

            // write to socket
            write(fd, byteBuffer, new NativeLong(byteBuffer.capacity()));
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
            LibC.close(fd);
        }
    }

    @SuppressWarnings({ "java:S116", "java:S1104", "java:S2160" })
    @FieldOrder({ "name" })
    public static class SockName extends Structure {
        public static final int LENGTH = 16;
        public byte[] name = new byte[LENGTH];
    }
}
