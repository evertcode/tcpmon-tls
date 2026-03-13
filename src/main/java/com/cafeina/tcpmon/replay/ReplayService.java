package com.cafeina.tcpmon.replay;

import com.cafeina.tcpmon.ProxyConfig;
import com.cafeina.tcpmon.TransportMode;
import com.cafeina.tcpmon.tls.TlsContextFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class ReplayService {
    private final ProxyConfig config;

    public ReplayService(ProxyConfig config) {
        this.config = config;
    }

    public Map<String, Object> replay(byte[] payload, ReplayDestination destination) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(1);
        CountDownLatch latch = new CountDownLatch(1);
        Instant startedAt = Instant.now();
        try {
            Endpoint endpoint = resolveEndpoint(destination);
            SslContext sslContext = resolveSslContext(destination);
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            if (sslContext != null) {
                                channel.pipeline().addLast("ssl", newClientHandler(destination, endpoint, sslContext, channel.alloc()));
                            }
                            channel.pipeline().addLast(new io.netty.channel.SimpleChannelInboundHandler<io.netty.buffer.ByteBuf>() {
                                @Override
                                public void channelActive(io.netty.channel.ChannelHandlerContext context) {
                                    context.writeAndFlush(Unpooled.wrappedBuffer(payload))
                                            .addListener((ChannelFutureListener) future -> context.close());
                                }

                                @Override
                                protected void channelRead0(io.netty.channel.ChannelHandlerContext context, io.netty.buffer.ByteBuf msg) {
                                    msg.skipBytes(msg.readableBytes());
                                }

                                @Override
                                public void channelInactive(io.netty.channel.ChannelHandlerContext context) {
                                    latch.countDown();
                                }

                                @Override
                                public void exceptionCaught(io.netty.channel.ChannelHandlerContext context, Throwable cause) {
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
                    "durationMillis", Duration.between(startedAt, Instant.now()).toMillis(),
                    "destination", destination.name().toLowerCase(),
                    "target", endpoint.host() + ":" + endpoint.port());
        } finally {
            group.shutdownGracefully().sync();
        }
    }

    Endpoint resolveEndpoint(ReplayDestination destination) {
        return switch (destination) {
            case LISTENER -> new Endpoint(config.listener().host(), config.listener().port());
            case TARGET -> new Endpoint(config.target().host(), config.target().port());
        };
    }

    private SslContext resolveSslContext(ReplayDestination destination) throws Exception {
        return switch (destination) {
            case TARGET -> config.target().transportMode() == TransportMode.TLS
                    ? TlsContextFactory.buildClientContext(config)
                    : null;
            case LISTENER -> {
                if (config.listener().transportMode() != TransportMode.TLS) {
                    yield null;
                }
                if (config.listener().clientAuthMode() == com.cafeina.tcpmon.ClientAuthMode.REQUIRE) {
                    throw new IllegalStateException("Replay to a TLS listener with required client auth is not supported yet");
                }
                yield SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();
            }
        };
    }

    private io.netty.handler.ssl.SslHandler newClientHandler(
            ReplayDestination destination,
            Endpoint endpoint,
            SslContext sslContext,
            ByteBufAllocator allocator) {
        return switch (destination) {
            case TARGET -> TlsContextFactory.newClientHandler(config, sslContext, allocator);
            case LISTENER -> sslContext.newHandler(allocator, endpoint.host(), endpoint.port());
        };
    }

    record Endpoint(String host, int port) {
    }
}
