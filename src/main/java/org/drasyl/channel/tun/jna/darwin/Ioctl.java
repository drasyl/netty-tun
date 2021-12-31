package org.drasyl.channel.tun.jna.darwin;

import com.sun.jna.NativeLong;

/**
 * JNA mapping for <a href="https://opensource.apple.com/source/xnu/xnu-2782.30.5/bsd/sys/ioctl.h.auto.html">ioctl.h</a>.
 */
@SuppressWarnings("java:S2974")
final class Ioctl {
    // get MTU size
    public static final NativeLong SIOCGIFMTU = new NativeLong(0xc0206933L);

    private Ioctl() {
        // JNA mapping
    }
}
