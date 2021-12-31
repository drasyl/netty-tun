package org.drasyl.channel.tun.jna.linux;

import com.sun.jna.NativeLong;

/**
 * JNA mapping for <a href="https://github.com/torvalds/linux/blob/master/include/uapi/linux/if_tun.h">if_tun.h</a>.
 */
public final class IfTun {
    static final NativeLong TUNSETIFF = new NativeLong(0x400454caL);
    // TUN device (no Ethernet headers)
    static final short IFF_TUN = 0x0001;
    // do not provide packet information
    static final short IFF_NO_PI = 0x1000;

    private IfTun() {
        // JNA mapping
    }
}
