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
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.drasyl.channel.wintun.win32.Guid;
import org.drasyl.channel.wintun.win32.WinDef;
import org.drasyl.channel.wintun.win32.WinNT;

@UnstableApi
public final class Wintun {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Wintun.class);
    private static final Throwable UNAVAILABILITY_CAUSE;

    private static void loadNativeLibrary() {
        if (!PlatformDependent.isWindows()) {
            throw new IllegalStateException("Only supported on Windows");
        }
        String staticLibName = "wintun";
        String sharedLibName = staticLibName + '_' + PlatformDependent.normalizedArch();
        ClassLoader cl = PlatformDependent.getClassLoader(Wintun.class);
        try {
            NativeLibraryLoader.load(sharedLibName, cl);
        } catch (UnsatisfiedLinkError e1) {
            try {
                NativeLibraryLoader.load(staticLibName, cl);
                logger.debug("Failed to load {}", sharedLibName, e1);
            } catch (UnsatisfiedLinkError e2) {
                ThrowableUtil.addSuppressed(e1, e2);
                throw e1;
            }
        }
    }

    static {
        Throwable cause = null;
        if (SystemPropertyUtil.getBoolean("io.netty.transport.noNative", false)) {
            cause = new UnsupportedOperationException(
                    "Native transport was explicit disabled with -Dio.netty.transport.noNative=true");
        } else {
            try {
                loadNativeLibrary();
            } catch (Throwable t) {
                cause = t;
            }
        }

        UNAVAILABILITY_CAUSE = cause;
    }

    /**
     * Ensure that <a href="https://netty.io/wiki/native-transports.html">{@code netty-transport-native-kqueue}</a> is
     * available.
     *
     * @throws UnsatisfiedLinkError if unavailable
     */
    public static void ensureAvailability() {
        if (UNAVAILABILITY_CAUSE != null) {
            throw (Error) new UnsatisfiedLinkError(
                    "failed to load the required native library").initCause(UNAVAILABILITY_CAUSE);
        }
    }

    /**
     * Creates a new Wintun adapter.
     *
     * @param Name          The requested name of the adapter. Zero-terminated string of up to
     *                      MAX_ADAPTER_NAME-1 characters.
     * @param TunnelType    Name of the adapter tunnel type. Zero-terminated string of up to
     *                      MAX_ADAPTER_NAME-1 characters.
     * @param RequestedGUID The GUID of the created network adapter, which then influences NLA
     *                      generation deterministically. If it is set to NULL, the GUID is chosen
     *                      by the system at random, and hence a new NLA entry is created for each
     *                      new adapter. It is called "requested" GUID because the API it uses is
     *                      completely undocumented, and so there could be minor interesting
     *                      complications with its usage.
     * @return If the function succeeds, the return value is the adapter handle. Must be released
     * with WintunCloseAdapter. If the function fails, the return value is NULL. To get extended
     * error information, call GetLastError.
     * @throws LastErrorException
     */
    public static native WINTUN_ADAPTER_HANDLE WintunCreateAdapter(final WString Name,
                                                                   final WString TunnelType,
                                                                   final Guid.GUID RequestedGUID) throws LastErrorException;

    /**
     * Releases Wintun adapter resources and, if adapter was created with WintunCreateAdapter,
     * removes adapter.
     *
     * @param Adapter Adapter handle obtained with WintunCreateAdapter or WintunOpenAdapter.
     * @throws LastErrorException
     */
    public static native void WintunCloseAdapter(final WINTUN_ADAPTER_HANDLE Adapter) throws LastErrorException;

    /**
     * Returns the LUID of the adapter.
     *
     * @param Adapter Adapter handle obtained with WintunOpenAdapter or WintunCreateAdapter.
     * @param Luid    Pointer to LUID to receive adapter LUID.
     * @throws LastErrorException
     */
    public static native void WintunGetAdapterLUID(final WINTUN_ADAPTER_HANDLE Adapter,
                                                   final Pointer Luid) throws LastErrorException;

    /**
     * Determines the version of the Wintun driver currently loaded.
     *
     * @return If the function succeeds, the return value is the version number. If the function
     * fails, the return value is zero. To get extended error information, call GetLastError.
     * Possible errors include the following: ERROR_FILE_NOT_FOUND Wintun not loaded
     * @throws LastErrorException
     */
    public static native WinDef.DWORD WintunGetRunningDriverVersion() throws LastErrorException;

    /**
     * Starts Wintun session.
     *
     * @param Adapter  Adapter handle obtained with WintunOpenAdapter or WintunCreateAdapter.
     * @param Capacity Rings capacity. Must be between WINTUN_MIN_RING_CAPACITY and
     *                 WINTUN_MAX_RING_CAPACITY (incl.) Must be a power of two.
     * @return Wintun session handle. Must be released with WintunEndSession. If the function fails,
     * the return value is NULL. To get extended error information, call GetLastError.
     * @throws LastErrorException
     */
    public static native WINTUN_SESSION_HANDLE WintunStartSession(final WINTUN_ADAPTER_HANDLE Adapter,
                                                                  final WinDef.DWORD Capacity) throws LastErrorException;

    /**
     * Ends Wintun session.
     *
     * @param Session Wintun session handle obtained with WintunStartSession.
     * @throws LastErrorException
     */
    public static native void WintunEndSession(final WINTUN_SESSION_HANDLE Session) throws LastErrorException;

    /**
     * Gets Wintun session's read-wait event handle.
     *
     * @param Session Wintun session handle obtained with WintunStartSession.
     * @return Pointer to receive event handle to wait for available data when reading. Should
     * WintunReceivePackets return ERROR_NO_MORE_ITEMS (after spinning on it for a while under heavy
     * load), wait for this event to become signaled before retrying WintunReceivePackets. Do not
     * call CloseHandle on this event - it is managed by the session.
     * @throws LastErrorException
     */
    public static native WinNT.HANDLE WintunGetReadWaitEvent(WINTUN_SESSION_HANDLE Session) throws LastErrorException;

    /**
     * Retrieves one or packet. After the packet content is consumed, call
     * WintunReleaseReceivePacket with Packet returned from this function to release internal
     * buffer. This function is thread-safe.
     *
     * @param Session    Wintun session handle obtained with WintunStartSession.
     * @param PacketSize Pointer to receive packet size.
     * @return Pointer to layer 3 IPv4 or IPv6 packet. Client may modify its content at will. If the
     * function fails, the return value is NULL. To get extended error information, call
     * GetLastError. Possible errors include the following: ERROR_HANDLE_EOF Wintun adapter is
     * terminating; ERROR_NO_MORE_ITEMS Wintun buffer is exhausted; ERROR_INVALID_DATA Wintun buffer
     * is corrupt.
     * @throws LastErrorException
     */
    public static native Pointer WintunReceivePacket(final WINTUN_SESSION_HANDLE Session,
                                                     final Pointer PacketSize) throws LastErrorException;

    /**
     * Releases internal buffer after the received packet has been processed by the client. This
     * function is thread-safe.
     *
     * @param Session Wintun session handle obtained with WintunStartSession.
     * @param Packet  Packet obtained with WintunReceivePacket.
     * @throws LastErrorException
     */
    public static native void WintunReleaseReceivePacket(final WINTUN_SESSION_HANDLE Session,
                                                         final Pointer Packet) throws LastErrorException;

    /**
     * Allocates memory for a packet to send. After the memory is filled with packet data, call
     * WintunSendPacket to send and release internal buffer. WintunAllocateSendPacket is thread-safe
     * and the WintunAllocateSendPacket order of calls define the packet sending order.
     *
     * @param Session    Wintun session handle obtained with WintunStartSession.
     * @param PacketSize Exact packet size. Must be less or equal to WINTUN_MAX_IP_PACKET_SIZE.
     * @return
     * @throws LastErrorException
     */
    public static native Pointer WintunAllocateSendPacket(final WINTUN_SESSION_HANDLE Session,
                                                          final WinDef.DWORD PacketSize) throws LastErrorException;

    /**
     * Sends the packet and releases internal buffer. WintunSendPacket is thread-safe, but the
     * WintunAllocateSendPacket order of calls define the packet sending order. This means the
     * packet is not guaranteed to be sent in the WintunSendPacket yet.
     *
     * @param Session Wintun session handle obtained with WintunStartSession.
     * @param Packet  Packet obtained with WintunAllocateSendPacket.
     * @throws LastErrorException
     */
    public static native void WintunSendPacket(WINTUN_SESSION_HANDLE Session,
                                               final Pointer Packet) throws LastErrorException;

    /**
     * A handle representing Wintun adapter.
     */
    @SuppressWarnings("java:S101")
    public static class WINTUN_ADAPTER_HANDLE extends WinNT.HANDLE {
    }

    /**
     * A handle representing Wintun session.
     */
    @SuppressWarnings("java:S101")
    public static class WINTUN_SESSION_HANDLE extends WinNT.HANDLE {
    }
}
