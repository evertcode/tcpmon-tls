package com.cafeina.tcpmon.proxy;

import com.cafeina.tcpmon.ClientAuthMode;
import com.cafeina.tcpmon.InterceptMode;
import com.cafeina.tcpmon.ListenerConfig;
import com.cafeina.tcpmon.ProxyConfig;
import com.cafeina.tcpmon.TargetConfig;
import com.cafeina.tcpmon.TlsMaterial;
import com.cafeina.tcpmon.TransportMode;
import com.cafeina.tcpmon.UiConfig;
import com.cafeina.tcpmon.session.SessionStore;
import com.cafeina.tcpmon.util.JsonSupport;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.bootstrap.Bootstrap;
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
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TcpMonProxyIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void proxiesPlainTcpTraffic() throws Exception {
        try (EchoServer echoServer = EchoServer.plain();
             SessionStore store = new SessionStore(tempDir.resolve("plain-sessions"), JsonSupport.objectMapper())) {
            int proxyPort = freePort();
            ProxyConfig config = new ProxyConfig(
                    new ListenerConfig("127.0.0.1", proxyPort, TransportMode.PLAIN, ClientAuthMode.NONE, emptyTls()),
                    new TargetConfig("127.0.0.1", echoServer.port(), TransportMode.PLAIN, null, false, false, false, emptyTls()),
                    new UiConfig("127.0.0.1", 0, false),
                    tempDir.resolve("plain-sessions"),
                    InterceptMode.NONE,
                    List.of("TLSv1.3", "TLSv1.2"),
                    List.of());

            try (TcpMonProxy proxy = new TcpMonProxy(config, store)) {
                proxy.start();
                try (Socket socket = new Socket("127.0.0.1", proxyPort);
                     OutputStream outputStream = socket.getOutputStream();
                     InputStream inputStream = socket.getInputStream()) {
                    socket.setSoTimeout(3_000);
                    outputStream.write("hello".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    byte[] response = inputStream.readNBytes(5);
                    assertEquals("hello", new String(response, StandardCharsets.UTF_8));
                }
                assertEquals(1, store.listSessions().size());
            }
        }
    }

    @Test
    void proxiesTlsInboundAndOutboundTraffic() throws Exception {
        SelfSignedCertificate backendCertificate = new SelfSignedCertificate("backend.local");
        SelfSignedCertificate listenerCertificate = new SelfSignedCertificate("listener.local");
        try (TlsEchoServer echoServer = new TlsEchoServer(backendCertificate);
             SessionStore store = new SessionStore(tempDir.resolve("tls-sessions"), JsonSupport.objectMapper())) {
            int proxyPort = freePort();
            ProxyConfig config = new ProxyConfig(
                    new ListenerConfig(
                            "127.0.0.1",
                            proxyPort,
                            TransportMode.TLS,
                            ClientAuthMode.NONE,
                            new TlsMaterial(
                                    listenerCertificate.certificate().toPath(),
                                    listenerCertificate.privateKey().toPath(),
                                    null,
                                    null,
                                    null,
                                    null,
                                    "PKCS12",
                                    "PKCS12")),
                    new TargetConfig(
                            "127.0.0.1",
                            echoServer.port(),
                            TransportMode.TLS,
                            "backend.local",
                            false,
                            false,
                            false,
                            new TlsMaterial(
                                    null,
                                    null,
                                    null,
                                    null,
                                    backendCertificate.certificate().toPath(),
                                    null,
                                    "PKCS12",
                                    "PKCS12")),
                    new UiConfig("127.0.0.1", 0, false),
                    tempDir.resolve("tls-sessions"),
                    InterceptMode.NONE,
                    List.of("TLSv1.3", "TLSv1.2"),
                    List.of());

            try (TcpMonProxy proxy = new TcpMonProxy(config, store)) {
                proxy.start();
                String response = tlsClientRequest(proxyPort, "secure");
                assertEquals("secure", response);
                assertEquals(1, store.listSessions().size());
                String sessionId = store.listSessions().getFirst().get("sessionId").toString();
                var details = store.sessionDetails(sessionId);
                assertTrue(details.toString().contains("cipherSuite"));
            }
        } finally {
            backendCertificate.delete();
            listenerCertificate.delete();
        }
    }

    private static String tlsClientRequest(int port, String payload) throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        ArrayBlockingQueue<String> responses = new ArrayBlockingQueue<>(1);
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) throws Exception {
                            channel.pipeline().addLast(SslContextBuilder.forClient()
                                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                    .build()
                                    .newHandler(channel.alloc()));
                            channel.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                                @Override
                                public void channelActive(ChannelHandlerContext context) {
                                    context.writeAndFlush(Unpooled.wrappedBuffer(payload.getBytes(StandardCharsets.UTF_8)));
                                }

                                @Override
                                protected void channelRead0(ChannelHandlerContext context, ByteBuf message) {
                                    byte[] bytes = new byte[message.readableBytes()];
                                    message.readBytes(bytes);
                                    responses.offer(new String(bytes, StandardCharsets.UTF_8));
                                    context.close();
                                }

                                @Override
                                public void channelInactive(ChannelHandlerContext context) {
                                    latch.countDown();
                                }
                            });
                        }
                    });

            Channel channel = bootstrap.connect("127.0.0.1", port).sync().channel();
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

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static final class EchoServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread thread;

        private EchoServer(ServerSocket serverSocket, Thread thread) {
            this.serverSocket = serverSocket;
            this.thread = thread;
        }

        static EchoServer plain() throws Exception {
            ServerSocket socket = new ServerSocket(0);
            Thread thread = Thread.ofVirtual().start(() -> {
                try (Socket client = socket.accept();
                     InputStream inputStream = client.getInputStream();
                     OutputStream outputStream = client.getOutputStream()) {
                    byte[] bytes = inputStream.readNBytes(5);
                    outputStream.write(bytes);
                    outputStream.flush();
                } catch (Exception ignored) {
                }
            });
            return new EchoServer(socket, thread);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            thread.join(1_000);
        }
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
            return ((java.net.InetSocketAddress) channel.localAddress()).getPort();
        }

        @Override
        public void close() {
            channel.close().syncUninterruptibly();
            boss.shutdownGracefully().syncUninterruptibly();
            worker.shutdownGracefully().syncUninterruptibly();
        }
    }
}
