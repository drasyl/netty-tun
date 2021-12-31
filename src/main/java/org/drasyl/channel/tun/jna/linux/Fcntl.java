package org.drasyl.channel.tun.jna.linux;

/**
 * JNA mapping for <a href="https://github.com/torvalds/linux/blob/a8ad9a2434dc7967ab285437f443cae633b6fc1c/include/uapi/asm-generic/fcntl.h">fcntl.h</a>.
 */
final class Fcntl {
    // open for reading and writing
    public static final int O_RDWR = 2;

    private Fcntl() {
        // JNA mapping
    }
}
