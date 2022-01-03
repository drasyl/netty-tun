# netty-tun [![MIT License](https://img.shields.io/badge/license-MIT-blue)](https://opensource.org/licenses/MIT)

netty channel communicating via TUN devices (tested on macOS, Ubuntu/Linux, and Windows 10/11).

## How to use

```java
EventLoopGroup group = new NioEventLoopGroup(1);
try {
    final Bootstrap b = new Bootstrap()
        .group(group)
        .channel(TunChannel.class)
        .handler(...);
    final Channel ch = b.bind(new TunAddress()).sync().channel();
    // send/receive messages of type TunPacket...
    ch.closeFuture().sync();
}
finally {
    group.shutdownGracefully();
}
```

You can now assign an IP address and netmask to the created network interface (you can query the
actual interface name by
calling [Channel#localAddress()](https://netty.io/4.1/api/io/netty/channel/Channel.html#localAddress--)):

```bash
# macOS
sudo /sbin/ifconfig utun0 add 10.10.10.10 10.10.10.10
sudo /sbin/ifconfig utun0 up
sudo /sbin/route add -net 10.10.10.0/24 -iface utun0

# Linux
sudo /sbin/ip addr add 10.10.10.10/24 dev utun0
sudo /sbin/ip link set dev utun0 up
```

```powershell
# Windows
$InterfaceIndex = Get-NetAdapter -Name utun0 | select ifIndex -expandproperty ifIndex
New-NetIPAddress -InterfaceIndex $InterfaceIndex -IPAddress 10.10.10.10 -PrefixLength 24
```
