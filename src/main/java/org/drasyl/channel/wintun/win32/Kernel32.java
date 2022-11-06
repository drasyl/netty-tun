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
package org.drasyl.channel.wintun.win32;

import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.drasyl.channel.wintun.win32.WinNT.HANDLE;

/**
 * Interface definitions for <code>kernel32.dll</code>. Includes additional alternate mappings from
 * {@link WinNT} which make use of NIO buffers, {@link Wincon} for console API.
 */
@SuppressWarnings("java:S100")
public interface Kernel32 extends StdCallLibrary {
    /**
     * The instance.
     */
    Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class, W32APIOptions.DEFAULT_OPTIONS);

    /**
     * Waits until the specified object is in the signaled state or the time-out interval elapses.
     * To enter an alertable wait state, use the WaitForSingleObjectEx function. To wait for
     * multiple objects, use the WaitForMultipleObjects.
     *
     * @param hHandle        A handle to the object. For a list of the object types whose handles
     *                       can be specified, see the following Remarks section. If this handle is
     *                       closed while the wait is still pending, the function's behavior is
     *                       undefined. The handle must have the SYNCHRONIZE access right. For more
     *                       information, see Standard Access Rights.
     * @param dwMilliseconds The time-out interval, in milliseconds. If a nonzero value is
     *                       specified, the function waits until the object is signaled or the
     *                       interval elapses. If dwMilliseconds is zero, the function does not
     *                       enter a wait state if the object is not signaled; it always returns
     *                       immediately. If dwMilliseconds is INFINITE, the function will return
     *                       only when the object is signaled.
     * @return If the function succeeds, the return value indicates the event that caused the
     * function to return.
     */
    int WaitForSingleObject(HANDLE hHandle, int dwMilliseconds);
}
