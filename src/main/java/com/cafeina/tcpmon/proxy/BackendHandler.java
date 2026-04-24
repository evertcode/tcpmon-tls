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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.security.cert.Certificate;
import java.util.LinkedHashMap;
import java.util.Map;

final class BackendHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(BackendHandler.class);
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
            sessionStore.recordLifecycleAsync(sessionId, "DROP", Map.of("reason", "client channel unavailable"));
            log.warn("Dropping target payload sessionId={} bytes={} reason=client channel unavailable",
                    sessionId, payload.length);
            return;
        }
        log.trace("Forwarding target payload sessionId={} bytes={} intercepted={}",
                sessionId, payload.length, config.interceptMode().intercepts(Direction.TARGET_TO_CLIENT));

        if (config.interceptMode().intercepts(Direction.TARGET_TO_CLIENT)) {
            PendingPayload pendingPayload = sessionStore.addPending(
                    sessionId,
                    Direction.TARGET_TO_CLIENT,
                    payload,
                    bytes -> inboundChannel.writeAndFlush(Unpooled.wrappedBuffer(bytes)));
            sessionStore.recordPayloadAsync(sessionId, Direction.TARGET_TO_CLIENT, payload, pendingPayload.pendingId(), Map.of("intercepted", true));
        } else {
            sessionStore.recordPayloadAsync(sessionId, Direction.TARGET_TO_CLIENT, payload, null, Map.of("intercepted", false));
            inboundChannel.writeAndFlush(Unpooled.wrappedBuffer(payload));
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) {
        FrontendHandler.closeOnFlush(inboundChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        sessionStore.recordLifecycleAsync(sessionId, "TARGET_ERROR", Map.of("error", cause.toString()));
        log.warn("Target channel error sessionId={} error={}", sessionId, cause.toString());
        FrontendHandler.closeOnFlush(context.channel());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
        if (event instanceof SslHandshakeCompletionEvent handshakeEvent) {
            if (handshakeEvent.isSuccess()) {
                sessionStore.recordTlsAsync(sessionId, false, sslDetails(context.channel(), handshakeEvent));
                log.debug("Outbound TLS handshake succeeded sessionId={}", sessionId);
            } else {
                sessionStore.recordLifecycleAsync(sessionId, "TLS_OUTBOUND_FAILED", Map.of("error", handshakeEvent.cause().toString()));
                log.warn("Outbound TLS handshake failed sessionId={} error={}", sessionId, handshakeEvent.cause().toString());
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
