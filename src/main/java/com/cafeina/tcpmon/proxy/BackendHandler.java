package com.cafeina.tcpmon.proxy;

import com.cafeina.tcpmon.Direction;
import com.cafeina.tcpmon.ProxyConfig;
import com.cafeina.tcpmon.session.PendingPayload;
import com.cafeina.tcpmon.session.SessionStore;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.security.cert.Certificate;
import java.util.LinkedHashMap;
import java.util.Map;

final class BackendHandler extends ChannelInboundHandlerAdapter {
    private final Channel inboundChannel;
    private final String sessionId;
    private final ProxyConfig config;
    private final SessionStore sessionStore;

    BackendHandler(Channel inboundChannel, String sessionId, ProxyConfig config, SessionStore sessionStore) {
        this.inboundChannel = inboundChannel;
        this.sessionId = sessionId;
        this.config = config;
        this.sessionStore = sessionStore;
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

        if (!inboundChannel.isActive()) {
            sessionStore.recordLifecycle(sessionId, "DROP", Map.of("reason", "client channel unavailable"));
            return;
        }

        if (config.interceptMode().intercepts(Direction.TARGET_TO_CLIENT)) {
            PendingPayload pendingPayload = sessionStore.addPending(
                    sessionId,
                    Direction.TARGET_TO_CLIENT,
                    payload,
                    bytes -> inboundChannel.writeAndFlush(Unpooled.wrappedBuffer(bytes)));
            sessionStore.recordPayload(sessionId, Direction.TARGET_TO_CLIENT, payload, pendingPayload.pendingId(), Map.of("intercepted", true));
        } else {
            sessionStore.recordPayload(sessionId, Direction.TARGET_TO_CLIENT, payload, null, Map.of("intercepted", false));
            inboundChannel.writeAndFlush(Unpooled.wrappedBuffer(payload));
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) {
        FrontendHandler.closeOnFlush(inboundChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        sessionStore.recordLifecycle(sessionId, "TARGET_ERROR", Map.of("error", cause.toString()));
        FrontendHandler.closeOnFlush(context.channel());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
        if (event instanceof SslHandshakeCompletionEvent handshakeEvent) {
            if (handshakeEvent.isSuccess()) {
                sessionStore.recordTls(sessionId, false, sslDetails(context.channel(), handshakeEvent));
            } else {
                sessionStore.recordLifecycle(sessionId, "TLS_OUTBOUND_FAILED", Map.of("error", handshakeEvent.cause().toString()));
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
}
