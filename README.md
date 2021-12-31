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
