package com.cafeina.tcpmon.replay;

import com.cafeina.tcpmon.ProxyConfig;
import com.cafeina.tcpmon.RouteConfig;
import com.cafeina.tcpmon.TransportMode;
import com.cafeina.tcpmon.proxy.RouteRegistry;
import com.cafeina.tcpmon.tls.TlsContextFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ReplayService {
    private final ProxyConfig config;
    private final RouteRegistry registry;

    public ReplayService(ProxyConfig config, RouteRegistry registry) {
        this.config = config;
        this.registry = registry;
    }

    public Map<String, Object> replay(byte[] payload, String routeId, ReplayDestination destination) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(1);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger bytesReceived = new AtomicInteger();
        Instant startedAt = Instant.now();
        try {
            RouteConfig route = registry.findById(routeId)
                    .orElseThrow(() -> new IllegalArgumentException("Route not found: " + routeId));
            Endpoint endpoint = resolveEndpoint(route, destination);
            SslContext sslContext = resolveSslContext(route, destination);
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            if (sslContext != null) {
                                channel.pipeline().addLast("ssl", newClientHandler(route, destination, endpoint, sslContext, channel.alloc()));
                            }
                            channel.pipeline().addLast("read-timeout", new ReadTimeoutHandler(2));
                            channel.pipeline().addLast(new io.netty.channel.SimpleChannelInboundHandler<io.netty.buffer.ByteBuf>() {
                                @Override
                                public void channelActive(ChannelHandlerContext context) {
                                    context.writeAndFlush(Unpooled.wrappedBuffer(payload))
                                            .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                                }

                                @Override
                                protected void channelRead0(ChannelHandlerContext context, io.netty.buffer.ByteBuf msg) {
                                    bytesReceived.addAndGet(msg.readableBytes());
                                    msg.skipBytes(msg.readableBytes());
                                }

                                @Override
                                public void channelInactive(ChannelHandlerContext context) {
                                    latch.countDown();
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
                                    if (cause instanceof ReadTimeoutException) {
                                        context.close();
                                        return;
                                    }
                                    latch.countDown();
                                    context.close();
                                }
                            });
                        }
                    });

            bootstrap.connect(endpoint.host(), endpoint.port()).sync();
            latch.await(10, TimeUnit.SECONDS);
            return Map.of(
                    "status", "OK",
                    "bytesSent", payload.length,
                    "bytesReceived", bytesReceived.get(),
                    "durationMillis", Duration.between(startedAt, Instant.now()).toMillis(),
                    "destination", destination.name().toLowerCase(),
                    "target", endpoint.host() + ":" + endpoint.port());
        } finally {
            group.shutdownGracefully().sync();
        }
    }

    Endpoint resolveEndpoint(RouteConfig route, ReplayDestination destination) {
        return switch (destination) {
            case LISTENER -> new Endpoint(route.listener().host(), route.listener().port());
            case TARGET -> new Endpoint(route.target().host(), route.target().port());
        };
    }

    private SslContext resolveSslContext(RouteConfig route, ReplayDestination destination) throws Exception {
        return switch (destination) {
            case TARGET -> route.target().transportMode() == TransportMode.TLS
                    ? TlsContextFactory.buildClientContext(config, route)
                    : null;
            case LISTENER -> {
                if (route.listener().transportMode() != TransportMode.TLS) {
                    yield null;
                }
                if (route.listener().clientAuthMode() == com.cafeina.tcpmon.ClientAuthMode.REQUIRE) {
                    throw new IllegalStateException("Replay to a TLS listener with required client auth is not supported yet");
                }
                yield SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();
            }
        };
    }

    private io.netty.handler.ssl.SslHandler newClientHandler(
            RouteConfig route,
            ReplayDestination destination,
            Endpoint endpoint,
            SslContext sslContext,
            ByteBufAllocator allocator) {
        return switch (destination) {
            case TARGET -> TlsContextFactory.newClientHandler(route, sslContext, allocator);
            case LISTENER -> sslContext.newHandler(allocator, endpoint.host(), endpoint.port());
        };
    }

    record Endpoint(String host, int port) {
    }
}
