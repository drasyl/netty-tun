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

import io.netty.channel.AbstractChannel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.ThreadPerChannelEventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.drasyl.channel.tun.jna.TunDevice;
import org.drasyl.channel.tun.jna.darwin.DarwinTunDevice;
import org.drasyl.channel.tun.jna.linux.LinuxTunDevice;
import org.drasyl.channel.tun.jna.windows.WindowsTunDevice;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

public class TunChannel extends AbstractChannel {
    protected static final int SO_TIMEOUT = 1000;
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(TunChannel.class);
    private static final ChannelMetadata METADATA = new ChannelMetadata(true);
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(TunPacket.class) + ')';
    final Runnable readTask = new Runnable() {
        @Override
        public void run() {
            doRead();
        }
    };
    private final ChannelConfig config = new DefaultChannelConfig(this);
    private final List<Object> readBuf = new ArrayList<>();
    private final Runnable clearReadPendingRunnable = new Runnable() {
        @Override
        public void run() {
            readPending = false;
        }
    };
    boolean readPending;
    boolean readWhenInactive;
    private TunDevice device;
    private TunAddress localAddress;
    private static final EventLoopGroup read_event_loop = new NioEventLoopGroup(1);

    public TunChannel() {
        super(null);
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
//        System.out.println("TunChannel2.isOpen");
        return device == null || !device.isClosed();
    }

    @Override
    public boolean isActive() {
//        System.out.println("TunChannel2.isActive");
        return device != null && isOpen();
    }

    @Override
    protected SocketAddress localAddress0() {
        return localAddress;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return null;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
//        System.out.println("TunChannel2.doBind");
        if (PlatformDependent.isOsx()) {
            device = DarwinTunDevice.open(((TunAddress) localAddress).ifName());
        }
        else if (PlatformDependent.isWindows()) {
            device = WindowsTunDevice.open(((TunAddress) localAddress).ifName());
        }
        else {
            device = LinuxTunDevice.open(((TunAddress) localAddress).ifName());
        }
        this.localAddress = new TunAddress(device.name());
    }

    protected void doConnect(SocketAddress remoteAddress,
                             SocketAddress localAddress) throws Exception {
//        System.out.println("TunChannel2.doConnect");
        // do nothing
    }

    @Override
    protected void doDisconnect() throws Exception {
//        System.out.println("TunChannel2.doDisconnect");
        // do nothing
    }

    @Override
    protected void doClose() throws Exception {
//        System.out.println("TunChannel2.doClose");
        if (device != null) {
            device.close();
        }
    }

    protected int doReadMessages(List<Object> msgs) throws Exception {
//        System.out.println("TunChannel2.doReadMessages");
        final TunPacket msg = device.readPacket(alloc());
        msgs.add(msg);
        return 1;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
//        System.out.println("TunChannel2.doWrite");
        while (true) {
            final Object msg = in.current();
            if (msg == null) {
                break;
            }

            try {
                device.writePacket(alloc(), (TunPacket) (((TunPacket) msg).retain()));
            }
            finally {
                in.remove();
            }
        }
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof TunPacket) {
            return msg;
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    public TunDevice device() {
        return device;
    }

    protected void doRead() {
        if (!readPending) {
            // We have to check readPending here because the Runnable to read could have been scheduled and later
            // during the same read loop readPending was set to false.
            return;
        }
        // In OIO we should set readPending to false even if the read was not successful so we can schedule
        // another read on the event loop if no reads are done.
        readPending = false;

        final ChannelConfig config = config();
        final ChannelPipeline pipeline = pipeline();
        final RecvByteBufAllocator.Handle allocHandle = unsafe().recvBufAllocHandle();
        allocHandle.reset(config);

        boolean closed = false;
        Throwable exception = null;
        try {
            do {
                // Perform a read.
                int localRead = doReadMessages(readBuf);
                if (localRead == 0) {
                    break;
                }
                if (localRead < 0) {
                    closed = true;
                    break;
                }

                allocHandle.incMessagesRead(localRead);
            } while (allocHandle.continueReading());
        } catch (Throwable t) {
            exception = t;
        }

        boolean readData = false;
        int size = readBuf.size();
        if (size > 0) {
            readData = true;
            for (int i = 0; i < size; i++) {
                readPending = false;
                pipeline.fireChannelRead(readBuf.get(i));
            }
            readBuf.clear();
            allocHandle.readComplete();
            pipeline.fireChannelReadComplete();
        }

        if (exception != null) {
            if (exception instanceof IOException) {
                closed = true;
            }

            pipeline.fireExceptionCaught(exception);
        }

        if (closed) {
            if (isOpen()) {
                unsafe().close(unsafe().voidPromise());
            }
        } else if (readPending || config.isAutoRead() || !readData && isActive()) {
            // Reading 0 bytes could mean there is a SocketTimeout and no data was actually read, so we
            // should execute read() again because no data may have been read.
            read();
        }
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new DefaultOioUnsafe();
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof EventLoopGroup;
    }

    @Override
    protected void doBeginRead() throws Exception {
        if (readPending) {
            return;
        }
        if (!isActive()) {
            readWhenInactive = true;
            return;
        }

        readPending = true;
        read_event_loop.next().execute(readTask);
    }

    /**
     * @deprecated No longer supported.
     * No longer supported.
     */
    @Deprecated
    protected boolean isReadPending() {
        return readPending;
    }

    /**
     * @deprecated Use {@link #clearReadPending()} if appropriate instead.
     * No longer supported.
     */
    @Deprecated
    protected void setReadPending(final boolean readPending) {
        if (isRegistered()) {
            EventLoop eventLoop = read_event_loop.next();
            if (eventLoop.inEventLoop()) {
                this.readPending = readPending;
            } else {
                eventLoop.execute(new Runnable() {
                    @Override
                    public void run() {
                        TunChannel.this.readPending = readPending;
                    }
                });
            }
        } else {
            this.readPending = readPending;
        }
    }

    /**
     * Set read pending to {@code false}.
     */
    protected final void clearReadPending() {
        if (isRegistered()) {
            EventLoop eventLoop = read_event_loop.next();
            if (eventLoop.inEventLoop()) {
                readPending = false;
            } else {
                eventLoop.execute(clearReadPendingRunnable);
            }
        } else {
            // Best effort if we are not registered yet clear readPending. This happens during channel initialization.
            readPending = false;
        }
    }

    private final class DefaultOioUnsafe extends AbstractUnsafe {
        @Override
        public void connect(
                final SocketAddress remoteAddress,
                final SocketAddress localAddress, final ChannelPromise promise) {
            if (!promise.setUncancellable() || !ensureOpen(promise)) {
                return;
            }

            try {
                boolean wasActive = isActive();
                doConnect(remoteAddress, localAddress);

                // Get the state as trySuccess() may trigger an ChannelFutureListener that will close the Channel.
                // We still need to ensure we call fireChannelActive() in this case.
                boolean active = isActive();

                safeSetSuccess(promise);
                if (!wasActive && active) {
                    pipeline().fireChannelActive();
                }
            } catch (Throwable t) {
                safeSetFailure(promise, annotateConnectException(t, remoteAddress));
                closeIfClosed();
            }
        }
    }
}
