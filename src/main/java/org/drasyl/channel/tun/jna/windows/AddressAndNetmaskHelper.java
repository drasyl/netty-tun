package org.drasyl.channel.tun.jna.windows;

import com.sun.jna.LastErrorException;
import com.sun.jna.Pointer;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.channel.tun.jna.windows.WinDef.DWORD;
import org.drasyl.channel.tun.jna.windows.loader.LibraryLoader;

import java.io.IOException;

import static org.drasyl.channel.tun.jna.windows.loader.LibraryLoader.PREFER_SYSTEM;

/**
 * JNA based helper class to set the IP address and netmask for a given network device.
 */
public final class AddressAndNetmaskHelper {
    private static final String DEFAULT_MODE = SystemPropertyUtil.get("tun.native.mode", PREFER_SYSTEM);

    static {
        try {
            new LibraryLoader(AddressAndNetmaskHelper.class).loadLibrary(DEFAULT_MODE, "libdtun");
        }
        catch (final IOException e) {
            throw new RuntimeException(e); // NOSONAR
        }
    }

    private AddressAndNetmaskHelper() {
        // JNA mapping
    }

    public static native DWORD setIPv4AndNetmask(final Pointer luid,
                                                 final String ip,
                                                 final int mask) throws LastErrorException;

    public static native DWORD setIPv6AndNetmask(final Pointer luid,
                                                 final String ip,
                                                 final int mask) throws LastErrorException;
}
