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
import io.netty.channel.nio.NioEventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.PlatformDependent;
import org.drasyl.channel.tun.jna.TunDevice;
import org.drasyl.channel.tun.jna.darwin.DarwinTunDevice;
import org.drasyl.channel.tun.jna.linux.LinuxTunDevice;
import org.drasyl.channel.tun.jna.windows.WindowsTunDevice;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;

public class TunChannel extends AbstractChannel {
    enum State {OPEN, BOUND, CLOSED}

    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private final ChannelConfig config = new DefaultChannelConfig(this);
    private volatile State state;
    private volatile TunAddress localAddress; // NOSONAR
    private boolean readPending;
    private final EventLoopGroup readGroup = new NioEventLoopGroup(1);
    private final Runnable readTask = this::doRead;
    private TunDevice device;

    public TunChannel() {
        super(null);
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new TunChannelUnsafe();
    }

    @Override
    protected boolean isCompatible(final EventLoop loop) {
        return loop instanceof NioEventLoop;
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
    protected void doBind(final SocketAddress localAddress) throws IOException {
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

        state = State.BOUND;
        readGroup.execute(readTask);
    }

    @Override
    protected void doDisconnect() {
        doClose();
    }

    @Override
    protected void doClose() {
        localAddress = null;

        state = State.CLOSED;
    }

    @Override
    protected void doBeginRead() {
        readPending = true;
        readGroup.execute(readTask);
    }

    @Override
    protected void doWrite(final ChannelOutboundBuffer in) throws Exception {
        switch (state) {
            case OPEN:
                throw new NotYetConnectedException();
            case CLOSED:
                throw new ClosedChannelException();
            case BOUND:
                break;
        }

        while (true) {
            final Object msg = in.current();
            if (msg == null) {
                break;
            }

            device.writePacket(alloc(), (TunPacket) (((TunPacket) msg).retain()));

            in.remove();
        }
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return state != State.CLOSED;
    }

    @Override
    public boolean isActive() {
        return state == State.BOUND;
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    public TunDevice device() {
        return device;
    }

    private void doRead() {
        if (readPending && isActive()) {
            readPending = false;

            final EventExecutor executor = eventLoop();
            final ChannelPipeline p = pipeline();
            if (executor.inEventLoop()) {
                try {
                    p.fireChannelRead(device.readPacket(alloc()));
                    p.fireChannelReadComplete();
                }
                catch (final IOException e) {
                    p.fireExceptionCaught(e);
                }
            }
            else {
                eventLoop().execute(() -> {
                    try {
                        p.fireChannelRead(device.readPacket(alloc()));
                        p.fireChannelReadComplete();
                    }
                    catch (final IOException e) {
                        p.fireExceptionCaught(e);
                    }
                });
            }
        }
    }

    private class TunChannelUnsafe extends AbstractUnsafe {
        @Override
        public void connect(final SocketAddress remoteAddress,
                            final SocketAddress localAddress,
                            final ChannelPromise promise) {
            throw new AlreadyConnectedException();
        }
    }
}
