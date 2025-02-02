# netty-tun [![MIT License](https://img.shields.io/badge/license-MIT-blue)](https://opensource.org/licenses/MIT) [![Maven Central](https://img.shields.io/maven-central/v/org.drasyl/netty-tun.svg)](https://mvnrepository.com/artifact/org.drasyl/netty-tun) [![Javadoc](https://javadoc.io/badge2/org.drasyl/netty-tun/javadoc.svg)](https://www.javadoc.io/doc/org.drasyl/netty-tun)

netty channel communicating via TUN devices (tested on macOS, Ubuntu/Linux, and Windows 10/11).

> [!IMPORTANT]  
> If you would like this feature merged into Netty, I would greatly appreciate it if you could leave a :+1: on [my pull request](https://github.com/netty/netty/pull/12960). Thank you!

## How to use

```java
EventLoopGroup group = new DefaultEventLoopGroup(1);
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

### IP Address/Netmask

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

# to allow peers access local services, you may mark the tun network as "private"
Set-NetConnectionProfile -InterfaceIndex $InterfaceIndex -NetworkCategory "Private"
```

## MTU

The MTU size of the created network interface is by default 1500 on macOS/Linux and 65535 on Windows.

On macOS/Linux is can be adjusted by passing the channel option [`TunChannelOption.TUN_MTU`](https://github.com/drasyl/netty-tun/blob/master/src/main/java/org/drasyl/channel/tun/TunChannelOption.java) to the [`Bootstrap`](https://netty.io/4.1/api/io/netty/bootstrap/Bootstrap.html) object.

On Windows you have to use the following command:
```powershell
netsh interface ipv4 set subinterface tun0 mtu=1234 store=active
```
