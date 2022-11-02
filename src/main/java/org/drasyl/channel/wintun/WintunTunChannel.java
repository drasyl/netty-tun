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
package org.drasyl.channel.wintun;

import com.sun.jna.LastErrorException;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.AbstractChannel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.util.UncheckedBooleanSupplier;
import org.drasyl.channel.tun.Tun4Packet;
import org.drasyl.channel.tun.Tun6Packet;
import org.drasyl.channel.tun.TunPacket;
import org.drasyl.channel.tun.jna.windows.Guid;
import org.drasyl.channel.tun.jna.windows.WinDef;
import org.drasyl.channel.tun.jna.windows.Wintun;

import java.io.IOException;
import java.net.SocketAddress;

import static org.drasyl.channel.tun.jna.windows.Wintun.WintunCloseAdapter;
import static org.drasyl.channel.tun.jna.windows.Wintun.WintunCreateAdapter;
import static org.drasyl.channel.tun.jna.windows.Wintun.WintunEndSession;
import static org.drasyl.channel.tun.jna.windows.Wintun.WintunReceivePacket;
import static org.drasyl.channel.tun.jna.windows.Wintun.WintunReleaseReceivePacket;
import static org.drasyl.channel.tun.jna.windows.Wintun.WintunStartSession;

public class WintunTunChannel extends AbstractChannel {
    private static final WString TUNNEL_TYPE = new WString("drasyl");
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private final ChannelConfig config = new WintunTunChannelConfig(this);
    private volatile Wintun.WINTUN_ADAPTER_HANDLE adapter = null;
    volatile Wintun.WINTUN_SESSION_HANDLE session = null;

    public WintunTunChannel() {
        super(null);
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new WintunTunChannelUnsafe();
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof WintunEventLoop;
    }

    @Override
    protected SocketAddress localAddress0() {
        System.out.println("WintunTunChannel.localAddress0");
        return null;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        System.out.println("WintunTunChannel.remoteAddress0");
        return null;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        if (localAddress instanceof WintunAddress) {
            WintunAddress address = (WintunAddress) localAddress;
            String name = address.ifName();
            if (name == null) {
                name = "tun";
            }

            try {
                adapter = WintunCreateAdapter(new WString(name), TUNNEL_TYPE, Guid.GUID.newGuid());
                session = WintunStartSession(adapter, new WinDef.DWORD(0x400000));

                ((WintunEventLoop) eventLoop()).add(this);
            }
            catch (final LastErrorException e) {
                if (session != null) {
                    WintunEndSession(session);
                }

                if (adapter != null) {
                    WintunCloseAdapter(adapter);
                }

                throw new IOException(e);
            }
        } else {
            throw new Error("Unexpected SocketAddress implementation " + localAddress);
        }
    }

    @Override
    protected void doDisconnect() throws Exception {
        System.out.println("WintunTunChannel.doDisconnect");
    }

    @Override
    protected void doClose() throws Exception {
        System.out.println("WintunTunChannel.doClose");
    }

    @Override
    protected void doBeginRead() throws Exception {
        System.out.println("WintunTunChannel.doBeginRead");
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        System.out.println("WintunTunChannel.doWrite");
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        // FIXME: implement
        // FIXME: beide handler NULL oder device ist nicht closed
        return true;
    }

    @Override
    public boolean isActive() {
        return adapter != null && session != null && isOpen();
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    final class WintunTunChannelUnsafe extends AbstractUnsafe {
        @Override
        public void connect(SocketAddress remoteAddress,
                            SocketAddress localAddress,
                            ChannelPromise promise) {
            System.out.println("WintunTunChannelUnsafe.connect");
        }

        public void readReady() {
            RecvByteBufAllocator.Handle allocHandle = recvBufAllocHandle();
            readReady(allocHandle);
        }

        private void readReady(RecvByteBufAllocator.Handle allocHandle) {
            assert eventLoop().inEventLoop();
            final ChannelPipeline pipeline = pipeline();
            final ByteBufAllocator allocator = config.getAllocator();
            allocHandle.reset(config);

            Throwable exception = null;
            ByteBuf byteBuf = null;
            try {
                do {
                    byteBuf = allocHandle.allocate(allocator);
                    allocHandle.attemptedBytesRead(byteBuf.writableBytes());

                    final TunPacket packet;
                    allocHandle.lastBytesRead(doReadBytes(byteBuf));
                    if (allocHandle.lastBytesRead() <= 0) {
                        // nothing was read, release the buffer.
                        byteBuf.release();
                        byteBuf = null;
                        break;
                    }
                    final int ipVersion = byteBuf.getByte(0) >> 4;
                    if (ipVersion == 4) {
                        packet = new Tun4Packet(byteBuf);
                    }
                    else {
                        packet = new Tun6Packet(byteBuf);
                    }

                    allocHandle.incMessagesRead(1);

                    pipeline.fireChannelRead(packet);

                    byteBuf = null;
                } while (allocHandle.continueReading());
            } catch (Throwable t) {
                if (byteBuf != null) {
                    byteBuf.release();
                }
                exception = t;
            }

            allocHandle.readComplete();
            pipeline.fireChannelReadComplete();

            if (exception != null) {
                pipeline.fireExceptionCaught(exception);
            }
        }
    }

    private int doReadBytes(ByteBuf byteBuf) {
        // read bytes
        final Pointer packetSizePointer = new Memory(Native.POINTER_SIZE);
        final Pointer packetPointer = WintunReceivePacket(session, packetSizePointer);

        // shrink bytebuf to actual required size
        final int PacketSize = packetSizePointer.getInt(0);
        byteBuf.writeBytes(packetPointer.getByteArray(0, PacketSize));
        WintunReleaseReceivePacket(session, packetPointer);

        return PacketSize;
    }
}
