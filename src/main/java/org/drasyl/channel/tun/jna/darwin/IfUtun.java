package org.drasyl.channel.tun.jna.darwin;

/**
 * JNA mapping for <a href="https://opensource.apple.com/source/xnu/xnu-2050.18.24/bsd/net/if_utun.h.auto.html">if_utun.h</a>.
 */
final class IfUtun {
    static final String UTUN_CONTROL_NAME = "com.apple.net.utun_control";
    static final int UTUN_OPT_IFNAME = 2;

    private IfUtun() {
        // JNA mapping
    }
}
