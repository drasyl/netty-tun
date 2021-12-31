package org.drasyl.channel.tun.jna.linux;

import com.sun.jna.NativeLong;

/**
 * JNA mapping for <a href="https://github.com/torvalds/linux/blob/5bfc75d92efd494db37f5c4c173d3639d4772966/include/uapi/linux/sockios.h">sockios.h</a>.
 */
final class Sockios {
    // get MTU size
    public static final NativeLong SIOCGIFMTU = new NativeLong(0x8921L);

    private Sockios() {
        // JNA mapping
    }
}
