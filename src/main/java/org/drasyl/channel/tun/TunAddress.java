package org.drasyl.channel.tun;

import java.net.SocketAddress;

public class TunAddress extends SocketAddress {
    private static final long serialVersionUID = -584786182484350484L; // NOSONAR
    private final String ifName;

    public TunAddress(final String ifName) {
        this.ifName = ifName;
    }

    public TunAddress() {
        this(null);
    }

    public String ifName() {
        return ifName;
    }

    @Override
    public String toString() {
        if (ifName != null) {
            return ifName;
        }
        else {
            return "";
        }
    }
}
