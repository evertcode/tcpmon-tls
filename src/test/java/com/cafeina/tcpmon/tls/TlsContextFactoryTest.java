package com.cafeina.tcpmon.tls;

import com.cafeina.tcpmon.ClientAuthMode;
import com.cafeina.tcpmon.InterceptMode;
import com.cafeina.tcpmon.ListenerConfig;
import com.cafeina.tcpmon.ProxyConfig;
import com.cafeina.tcpmon.TargetConfig;
import com.cafeina.tcpmon.TlsMaterial;
import com.cafeina.tcpmon.TransportMode;
import com.cafeina.tcpmon.UiConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TlsContextFactoryTest {
    @TempDir
    Path tempDir;

    @Test
    void buildClientContextSupportsInsecureOutboundTls() throws Exception {
        SelfSignedCertificate certificate = new SelfSignedCertificate("backend.local");
        try (TlsEchoServer server = new TlsEchoServer(certificate)) {
            ProxyConfig config = new ProxyConfig(
                    new ListenerConfig("127.0.0.1", 0, TransportMode.PLAIN, ClientAuthMode.NONE, emptyTls()),
                    new TargetConfig("127.0.0.1", server.port(), TransportMode.TLS, "backend.local", true, false, false, emptyTls()),
                    new UiConfig("127.0.0.1", 0, false),
                    tempDir.resolve("sessions"),
                    InterceptMode.NONE,
                    List.of("TLSv1.3", "TLSv1.2"),
                    List.of());

            SslContext sslContext = TlsContextFactory.buildClientContext(config);
            String response = sendTlsPayload(sslContext, config, "hello");
            assertEquals("hello", response);
        } finally {
            certificate.delete();
        }
    }

    private static String sendTlsPayload(SslContext sslContext, ProxyConfig config, String payload) throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        ArrayBlockingQueue<String> responses = new ArrayBlockingQueue<>(1);
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            channel.pipeline().addLast(TlsContextFactory.newClientHandler(config, sslContext, channel.alloc()));
                            channel.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                                @Override
                                public void channelActive(ChannelHandlerContext context) {
                                    context.writeAndFlush(Unpooled.wrappedBuffer(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                                }

                                @Override
                                protected void channelRead0(ChannelHandlerContext context, ByteBuf message) {
                                    byte[] bytes = new byte[message.readableBytes()];
                                    message.readBytes(bytes);
                                    responses.offer(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                                    context.close();
                                }

                                @Override
                                public void channelInactive(ChannelHandlerContext context) {
                                    latch.countDown();
                                }
                            });
                        }
                    });

            Channel channel = bootstrap.connect(config.target().host(), config.target().port()).sync().channel();
            assertTrue(latch.await(10, TimeUnit.SECONDS));
            channel.close().syncUninterruptibly();
            return responses.poll(1, TimeUnit.SECONDS);
        } finally {
            group.shutdownGracefully().sync();
        }
    }

    private static TlsMaterial emptyTls() {
        return new TlsMaterial(null, null, null, null, null, null, "PKCS12", "PKCS12");
    }

    private static final class TlsEchoServer implements AutoCloseable {
        private final NioEventLoopGroup boss = new NioEventLoopGroup(1);
        private final NioEventLoopGroup worker = new NioEventLoopGroup(1);
        private final Channel channel;

        private TlsEchoServer(SelfSignedCertificate certificate) throws Exception {
            channel = new ServerBootstrap()
                    .group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey()).build().newHandler(ch.alloc()));
                            ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext context, ByteBuf message) {
                                    context.writeAndFlush(message.retainedDuplicate()).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                                }
                            });
                        }
                    })
                    .bind(0)
                    .sync()
                    .channel();
        }

        int port() {
            return ((InetSocketAddress) channel.localAddress()).getPort();
        }

        @Override
        public void close() {
            channel.close().syncUninterruptibly();
            boss.shutdownGracefully().syncUninterruptibly();
            worker.shutdownGracefully().syncUninterruptibly();
        }
    }
}
