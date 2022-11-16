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

import io.netty.channel.EventLoopGroup;
import io.netty.channel.EventLoopTaskQueueFactory;
import io.netty.channel.SelectStrategy;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.util.IntSupplier;
import io.netty.util.concurrent.RejectedExecutionHandler;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.drasyl.channel.wintun.win32.Kernel32;
import org.drasyl.channel.wintun.WintunTunChannel.WintunTunChannelUnsafe;

import java.util.Queue;
import java.util.concurrent.Executor;

import static java.lang.Math.min;
import static org.drasyl.channel.wintun.Native.WintunGetReadWaitEvent;

final class WintunEventLoop extends SingleThreadEventLoop {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(WintunEventLoop.class);
//    private static final AtomicIntegerFieldUpdater<WintunEventLoop> WAKEN_UP_UPDATER =
//            AtomicIntegerFieldUpdater.newUpdater(WintunEventLoop.class, "wakenUp");
    private final SelectStrategy selectStrategy;
    private final IntSupplier selectNowSupplier = new IntSupplier() {
        @Override
        public int get() throws Exception {
            // FIXME:
            return SelectStrategy.SELECT;
        }
    };

    private volatile int ioRatio = 50;
    private WintunTunChannel channel;

    WintunEventLoop(final EventLoopGroup parent,
                    final Executor executor,
                    final int maxEvents,
                    final SelectStrategy strategy,
                    final RejectedExecutionHandler rejectedExecutionHandler,
                    final EventLoopTaskQueueFactory taskQueueFactory,
                    final EventLoopTaskQueueFactory tailTaskQueueFactory) {
        super(parent, executor, false, newTaskQueue(taskQueueFactory), newTaskQueue(tailTaskQueueFactory), rejectedExecutionHandler);
        this.selectStrategy = ObjectUtil.checkNotNull(strategy, "strategy");
    }

    public void add(final WintunTunChannel ch) {
        this.channel = ch;
    }

    public void remove(WintunTunChannel ch) {
        this.channel = null;
    }

    private static Queue<Runnable> newTaskQueue(
            EventLoopTaskQueueFactory queueFactory) {
        if (queueFactory == null) {
            return newTaskQueue0(DEFAULT_MAX_PENDING_TASKS);
        }
        return queueFactory.newTaskQueue(DEFAULT_MAX_PENDING_TASKS);
    }

    private static Queue<Runnable> newTaskQueue0(int maxPendingTasks) {
        // This event loop never calls takeTask()
        return maxPendingTasks == Integer.MAX_VALUE ? PlatformDependent.<Runnable>newMpscQueue()
                : PlatformDependent.<Runnable>newMpscQueue(maxPendingTasks);
    }

    @Override
    protected void run() {
        System.out.println("WintunEventLoop.run");
        for (; ; ) {
            try {
                int strategy = selectStrategy.calculateStrategy(selectNowSupplier, hasTasks());
                switch (strategy) {
                    case SelectStrategy.CONTINUE:
                        logger.info("WintunEventLoop.run CONTINUE");
                        continue;

                    case SelectStrategy.BUSY_WAIT:
                        logger.info("WintunEventLoop.run BUSY_WAIT");
                        // fall-through to SELECT since the busy-wait is not supported with wintun
                        // FIXME: ist das so?

                    case SelectStrategy.SELECT:
                        logger.info("WintunEventLoop.run SELECT");
                        strategy = wintunWait(); // FIXME

                    default:
                }

                final int ioRatio = this.ioRatio;
                if (ioRatio == 100) {
                    try {
                        if (strategy > 0) {
                            processReady();
                        }
                    } finally {
                        runAllTasks();
                    }
                } else {
                    final long ioStartTime = System.nanoTime();

                    try {
                        if (strategy > 0) {
                            processReady();
                        }
                    } finally {
                        final long ioTime = System.nanoTime() - ioStartTime;
                        runAllTasks(ioTime * (100 - ioRatio) / ioRatio);
                    }
                }
            }
            catch (Error e) {
                throw e;
            }
            catch (Throwable t) {
                handleLoopException(t);
            }
            finally {
                // Always handle shutdown even if the loop processing threw an exception.
                try {
                    if (isShuttingDown()) {
                        closeAll();
                        if (confirmShutdown()) {
                            break;
                        }
                    }
                }
                catch (Error e) {
                    throw e;
                }
                catch (Throwable t) {
                    handleLoopException(t);
                }
            }
        }
    }

    private void processReady() {
        if (channel != null) {
            WintunTunChannelUnsafe unsafe = (WintunTunChannelUnsafe) channel.unsafe();
            unsafe.readReady();
        }
    }

    private int wintunWait() {
        if (channel != null) {
            long totalDelay = delayNanos(System.nanoTime()) / 1000000;
            int delayMillis = (int) min(totalDelay / 1000000L, Integer.MAX_VALUE);
            int res = Kernel32.INSTANCE.WaitForSingleObject(WintunGetReadWaitEvent(channel.session), (int) min(totalDelay - delayMillis, Integer.MAX_VALUE));
            logger.info("res = " + res);
            // 0 WAIT_OBJECT_0
            // 258 WAIT_TIMEOUT
            return res == 0 ? 1 : 0;
        }
        return 0;
    }

    private void closeAll() {
        if (channel != null) {
            channel.unsafe().close(channel.unsafe().voidPromise());
        }
    }

    private static void handleLoopException(Throwable t) {
        logger.warn("Unexpected exception in the selector loop.", t);

        // Prevent possible consecutive immediate failures that lead to
        // excessive CPU consumption.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // Ignore.
        }
    }
}
