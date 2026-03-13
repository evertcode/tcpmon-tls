package com.cafeina.tcpmon.proxy;

import com.cafeina.tcpmon.Direction;
import com.cafeina.tcpmon.ProxyConfig;
import com.cafeina.tcpmon.session.PendingPayload;
import com.cafeina.tcpmon.session.SessionStore;
import com.cafeina.tcpmon.tls.TlsContextFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.net.InetSocketAddress;
import java.security.cert.Certificate;
import java.util.LinkedHashMap;
import java.util.Map;

final class FrontendHandler extends ChannelInboundHandlerAdapter {
    private final ProxyConfig config;
    private final SessionStore sessionStore;
    private final SslContext outboundSslContext;
    private volatile Channel outboundChannel;
    private volatile String sessionId;

    FrontendHandler(ProxyConfig config, SessionStore sessionStore, SslContext outboundSslContext) {
        this.config = config;
        this.sessionStore = sessionStore;
        this.outboundSslContext = outboundSslContext;
    }

    @Override
    public void channelActive(ChannelHandlerContext context) {
        Channel inboundChannel = context.channel();
        inboundChannel.config().setAutoRead(false);
        InetSocketAddress client = (InetSocketAddress) inboundChannel.remoteAddress();
        InetSocketAddress listener = (InetSocketAddress) inboundChannel.localAddress();
        this.sessionId = sessionStore.openSession(
                client.getHostString() + ":" + client.getPort(),
                listener.getHostString() + ":" + listener.getPort(),
                config.target().host() + ":" + config.target().port());
        sessionStore.recordLifecycle(sessionId, "CLIENT_CONNECTED", Map.of("client", client.toString()));

        Bootstrap bootstrap = new Bootstrap()
                .group(inboundChannel.eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.AUTO_READ, true)
                .handler(new OutboundInitializer(inboundChannel, sessionId, config, sessionStore, outboundSslContext));

        bootstrap.connect(config.target().host(), config.target().port()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                outboundChannel = future.channel();
                sessionStore.recordLifecycle(sessionId, "TARGET_CONNECTED", Map.of("target", future.channel().remoteAddress().toString()));
                inboundChannel.config().setAutoRead(true);
                inboundChannel.read();
            } else {
                sessionStore.recordLifecycle(sessionId, "TARGET_CONNECT_FAILED", Map.of("error", future.cause().toString()));
                inboundChannel.close();
            }
        });
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) {
        if (!(message instanceof ByteBuf buffer)) {
            context.fireChannelRead(message);
            return;
        }

        byte[] payload = new byte[buffer.readableBytes()];
        buffer.readBytes(payload);
        buffer.release();
        HttpRequestRewriter.RewriteResult rewriteResult = config.target().rewriteHostHeader()
                ? HttpRequestRewriter.rewriteHostHeader(payload, config.target().host(), config.target().port())
                : new HttpRequestRewriter.RewriteResult(payload, false);
        byte[] outboundPayload = rewriteResult.payload();

        if (outboundChannel == null || !outboundChannel.isActive()) {
            sessionStore.recordLifecycle(sessionId, "DROP", Map.of("reason", "outbound channel unavailable"));
            return;
        }

        if (config.interceptMode().intercepts(Direction.CLIENT_TO_TARGET)) {
            PendingPayload pendingPayload = sessionStore.addPending(
                    sessionId,
                    Direction.CLIENT_TO_TARGET,
                    outboundPayload,
                    bytes -> outboundChannel.writeAndFlush(Unpooled.wrappedBuffer(bytes)));
            sessionStore.recordPayload(
                    sessionId,
                    Direction.CLIENT_TO_TARGET,
                    outboundPayload,
                    pendingPayload.pendingId(),
                    Map.of("intercepted", true, "hostRewritten", rewriteResult.rewritten()));
        } else {
            sessionStore.recordPayload(
                    sessionId,
                    Direction.CLIENT_TO_TARGET,
                    outboundPayload,
                    null,
                    Map.of("intercepted", false, "hostRewritten", rewriteResult.rewritten()));
            outboundChannel.writeAndFlush(Unpooled.wrappedBuffer(outboundPayload));
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) {
        closeOnFlush(outboundChannel);
        if (sessionId != null) {
            sessionStore.closeSession(sessionId, "CLIENT_CLOSED");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        if (sessionId != null) {
            sessionStore.recordLifecycle(sessionId, "CLIENT_ERROR", Map.of("error", cause.toString()));
        }
        closeOnFlush(context.channel());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
        if (event instanceof SslHandshakeCompletionEvent handshakeEvent && sessionId != null) {
            if (handshakeEvent.isSuccess()) {
                sessionStore.recordTls(sessionId, true, sslDetails(context.channel(), handshakeEvent));
            } else {
                sessionStore.recordLifecycle(sessionId, "TLS_INBOUND_FAILED", Map.of("error", handshakeEvent.cause().toString()));
            }
        }
        super.userEventTriggered(context, event);
    }

    private static Map<String, Object> sslDetails(Channel channel, SslHandshakeCompletionEvent event) {
        Map<String, Object> details = new LinkedHashMap<>();
        var session = ((io.netty.handler.ssl.SslHandler) channel.pipeline().get("ssl")).engine().getSession();
        details.put("protocol", session.getProtocol());
        details.put("cipherSuite", session.getCipherSuite());
        try {
            Certificate[] peerCertificates = session.getPeerCertificates();
            details.put("peerCertificates", peerCertificates.length);
        } catch (SSLPeerUnverifiedException ignored) {
            details.put("peerCertificates", 0);
        }
        details.put("handshake", event.toString());
        return details;
    }

    static void closeOnFlush(Channel channel) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private static final class OutboundInitializer extends io.netty.channel.ChannelInitializer<io.netty.channel.socket.SocketChannel> {
        private final Channel inboundChannel;
        private final String sessionId;
        private final ProxyConfig config;
        private final SessionStore sessionStore;
        private final SslContext outboundSslContext;

        private OutboundInitializer(Channel inboundChannel, String sessionId, ProxyConfig config, SessionStore sessionStore, SslContext outboundSslContext) {
            this.inboundChannel = inboundChannel;
            this.sessionId = sessionId;
            this.config = config;
            this.sessionStore = sessionStore;
            this.outboundSslContext = outboundSslContext;
        }

        @Override
        protected void initChannel(io.netty.channel.socket.SocketChannel channel) {
            if (outboundSslContext != null) {
                channel.pipeline().addLast("ssl", TlsContextFactory.newClientHandler(config, outboundSslContext, channel.alloc()));
            }
            channel.pipeline().addLast("backend", new BackendHandler(inboundChannel, sessionId, config, sessionStore));
        }
    }
}
