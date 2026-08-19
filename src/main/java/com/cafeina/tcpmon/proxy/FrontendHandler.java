package com.cafeina.tcpmon.proxy;

import com.cafeina.tcpmon.Direction;
import com.cafeina.tcpmon.MockRule;
import com.cafeina.tcpmon.ProxyConfig;
import com.cafeina.tcpmon.RouteConfig;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

final class FrontendHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(FrontendHandler.class);
    private final ProxyConfig config;
    private final RouteConfig route;
    private final RouteRegistry registry;
    private final SessionStore sessionStore;
    private final SslContext outboundSslContext;
    private volatile Channel outboundChannel;
    private volatile String sessionId;

    FrontendHandler(ProxyConfig config, RouteConfig route, RouteRegistry registry, SessionStore sessionStore, SslContext outboundSslContext) {
        this.config = config;
        this.route = route;
        this.registry = registry;
        this.sessionStore = sessionStore;
        this.outboundSslContext = outboundSslContext;
    }

    /**
     * Mock/intercept/delay settings must reflect the latest control-plane update even for
     * connections accepted before that update, so they are re-read from the registry per
     * request instead of relying on {@link #route}, which is fixed at connection-accept time
     * (target/listener/TLS identity legitimately stays pinned to the connection).
     */
    private RouteConfig liveRoute() {
        return registry.findById(route.id()).orElse(route);
    }

    @Override
    public void channelActive(ChannelHandlerContext context) {
        Channel inboundChannel = context.channel();
        inboundChannel.config().setAutoRead(false);
        InetSocketAddress client = (InetSocketAddress) inboundChannel.remoteAddress();
        InetSocketAddress listener = (InetSocketAddress) inboundChannel.localAddress();
        this.sessionId = sessionStore.openSession(
                route.id(),
                client.getHostString() + ":" + client.getPort(),
                listener.getHostString() + ":" + listener.getPort(),
                route.target().host() + ":" + route.target().port());
        log.debug("Client connected routeId={} sessionId={} client={} listener={}",
                route.id(), sessionId, client, listener);
        sessionStore.recordLifecycleAsync(sessionId, "CLIENT_CONNECTED", Map.of("client", client.toString()));

        Bootstrap bootstrap = new Bootstrap()
                .group(inboundChannel.eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.AUTO_READ, true)
                .handler(new OutboundInitializer(inboundChannel, sessionId, config, route, sessionStore, outboundSslContext));

        bootstrap.connect(route.target().host(), route.target().port()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                outboundChannel = future.channel();
                log.debug("Target connected routeId={} sessionId={} target={}",
                        route.id(), sessionId, future.channel().remoteAddress());
                sessionStore.recordLifecycleAsync(sessionId, "TARGET_CONNECTED", Map.of("target", future.channel().remoteAddress().toString()));
                inboundChannel.config().setAutoRead(true);
                inboundChannel.read();
            } else {
                sessionStore.recordLifecycleAsync(sessionId, "TARGET_CONNECT_FAILED", Map.of("error", future.cause().toString()));
                log.warn("Target connection failed routeId={} sessionId={} target={}:{} error={}",
                        route.id(), sessionId, route.target().host(), route.target().port(), future.cause().toString());
                if (!liveRoute().mockRules().isEmpty()) {
                    inboundChannel.config().setAutoRead(true);
                    inboundChannel.read();
                } else {
                    inboundChannel.close();
                }
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

        RouteConfig liveRoute = liveRoute();
        if (!liveRoute.mockRules().isEmpty()) {
            HttpRequestRewriter.RequestLine requestLine = HttpRequestRewriter.parseRequestLine(payload);
            Optional<MockRule> matchedRule = findMatchingMockRule(liveRoute, requestLine);
            if (matchedRule.isPresent()) {
                serveMock(context, liveRoute, matchedRule.get(), payload);
                return;
            }
        }

        HttpRequestRewriter.RewriteResult rewriteResult = route.target().rewriteHostHeader()
                ? HttpRequestRewriter.rewriteHostHeader(payload, route.target().host(), route.target().port())
                : new HttpRequestRewriter.RewriteResult(payload, false);
        byte[] outboundPayload = rewriteResult.payload();

        if (outboundChannel == null || !outboundChannel.isActive()) {
            sessionStore.recordLifecycleAsync(sessionId, "DROP", Map.of("reason", "outbound channel unavailable"));
            log.warn("Dropping client payload routeId={} sessionId={} bytes={} reason=outbound channel unavailable",
                    route.id(), sessionId, outboundPayload.length);
            return;
        }
        boolean intercept = shouldIntercept(liveRoute, outboundPayload);
        log.trace("Forwarding client payload routeId={} sessionId={} bytes={} intercepted={}",
                route.id(), sessionId, outboundPayload.length, intercept);

        if (intercept) {
            PendingPayload pendingPayload = sessionStore.addPending(
                    sessionId,
                    Direction.CLIENT_TO_TARGET,
                    outboundPayload,
                    bytes -> outboundChannel.writeAndFlush(Unpooled.wrappedBuffer(bytes)));
            sessionStore.recordPayloadAsync(
                    sessionId,
                    Direction.CLIENT_TO_TARGET,
                    outboundPayload,
                    pendingPayload.pendingId(),
                    Map.of("intercepted", true, "hostRewritten", rewriteResult.rewritten()));
        } else {
            sessionStore.recordPayloadAsync(
                    sessionId,
                    Direction.CLIENT_TO_TARGET,
                    outboundPayload,
                    null,
                    Map.of("intercepted", false, "hostRewritten", rewriteResult.rewritten()));
            if (liveRoute.requestDelayMs() > 0) {
                context.executor().schedule(
                        () -> outboundChannel.writeAndFlush(Unpooled.wrappedBuffer(outboundPayload)),
                        liveRoute.requestDelayMs(), TimeUnit.MILLISECONDS);
            } else {
                outboundChannel.writeAndFlush(Unpooled.wrappedBuffer(outboundPayload));
            }
        }
    }

    private boolean shouldIntercept(RouteConfig liveRoute, byte[] payload) {
        if (!config.interceptMode().intercepts(Direction.CLIENT_TO_TARGET)) {
            return false;
        }
        String method = liveRoute.interceptMethod();
        String pathContains = liveRoute.interceptPathContains();
        if (method == null && pathContains == null) {
            return true;
        }
        HttpRequestRewriter.RequestLine requestLine = HttpRequestRewriter.parseRequestLine(payload);
        if (requestLine == null) {
            return false;
        }
        if (method != null && !method.equalsIgnoreCase(requestLine.method())) {
            return false;
        }
        return pathContains == null || requestLine.path().contains(pathContains);
    }

    private Optional<MockRule> findMatchingMockRule(RouteConfig liveRoute, HttpRequestRewriter.RequestLine requestLine) {
        if (requestLine == null) {
            return Optional.empty();
        }
        for (MockRule rule : liveRoute.mockRules()) {
            if (rule.method() != null && !rule.method().equalsIgnoreCase(requestLine.method())) {
                continue;
            }
            if (rule.pathContains() != null && !requestLine.path().contains(rule.pathContains())) {
                continue;
            }
            return Optional.of(rule);
        }
        return Optional.empty();
    }

    private void serveMock(ChannelHandlerContext context, RouteConfig liveRoute, MockRule rule, byte[] requestPayload) {
        byte[] responseBytes = buildMockResponse(rule);
        log.trace("Serving mock response routeId={} sessionId={} status={} bytes={}",
                route.id(), sessionId, rule.statusCode(), responseBytes.length);
        sessionStore.recordPayloadAsync(sessionId, Direction.CLIENT_TO_TARGET, requestPayload, null,
                Map.of("intercepted", false, "hostRewritten", false));
        sessionStore.recordPayloadAsync(sessionId, Direction.TARGET_TO_CLIENT, responseBytes, null,
                Map.of("intercepted", false, "mocked", true));
        if (liveRoute.responseDelayMs() > 0) {
            context.executor().schedule(
                    () -> context.channel().writeAndFlush(Unpooled.wrappedBuffer(responseBytes)),
                    liveRoute.responseDelayMs(), TimeUnit.MILLISECONDS);
        } else {
            context.channel().writeAndFlush(Unpooled.wrappedBuffer(responseBytes));
        }
    }

    private byte[] buildMockResponse(MockRule rule) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (rule.headers() != null) {
            for (String line : rule.headers().split("\r?\n")) {
                if (line.isBlank()) {
                    continue;
                }
                int separator = line.indexOf(':');
                if (separator <= 0) {
                    continue;
                }
                headers.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
            }
        }
        byte[] body = (rule.body() == null ? "" : rule.body()).getBytes(StandardCharsets.UTF_8);
        boolean hasContentLength = headers.keySet().stream().anyMatch(k -> k.equalsIgnoreCase("content-length"));
        if (!hasContentLength) {
            headers.put("Content-Length", Integer.toString(body.length));
        }

        StringBuilder builder = new StringBuilder();
        builder.append("HTTP/1.1 ").append(rule.statusCode()).append(' ')
                .append(REASON_PHRASES.getOrDefault(rule.statusCode(), "")).append("\r\n");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        }
        builder.append("\r\n");

        byte[] head = builder.toString().getBytes(StandardCharsets.ISO_8859_1);
        byte[] result = new byte[head.length + body.length];
        System.arraycopy(head, 0, result, 0, head.length);
        System.arraycopy(body, 0, result, head.length, body.length);
        return result;
    }

    private static final Map<Integer, String> REASON_PHRASES = Map.ofEntries(
            Map.entry(200, "OK"), Map.entry(201, "Created"), Map.entry(204, "No Content"),
            Map.entry(301, "Moved Permanently"), Map.entry(302, "Found"), Map.entry(304, "Not Modified"),
            Map.entry(400, "Bad Request"), Map.entry(401, "Unauthorized"), Map.entry(403, "Forbidden"),
            Map.entry(404, "Not Found"), Map.entry(405, "Method Not Allowed"), Map.entry(409, "Conflict"),
            Map.entry(422, "Unprocessable Entity"), Map.entry(429, "Too Many Requests"),
            Map.entry(500, "Internal Server Error"), Map.entry(502, "Bad Gateway"), Map.entry(503, "Service Unavailable"));

    @Override
    public void channelInactive(ChannelHandlerContext context) {
        if (route.requestDelayMs() > 0) {
            context.executor().schedule(() -> closeOnFlush(outboundChannel), route.requestDelayMs(), TimeUnit.MILLISECONDS);
        } else {
            closeOnFlush(outboundChannel);
        }
        if (sessionId != null) {
            sessionStore.closeSessionAsync(sessionId, "CLIENT_CLOSED");
            log.debug("Client disconnected routeId={} sessionId={}", route.id(), sessionId);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        if (sessionId != null) {
            sessionStore.recordLifecycleAsync(sessionId, "CLIENT_ERROR", Map.of("error", cause.toString()));
            log.warn("Client channel error routeId={} sessionId={} error={}", route.id(), sessionId, cause.toString());
        }
        closeOnFlush(context.channel());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
        if (event instanceof SslHandshakeCompletionEvent handshakeEvent && sessionId != null) {
            if (handshakeEvent.isSuccess()) {
                sessionStore.recordTlsAsync(sessionId, true, sslDetails(context.channel(), handshakeEvent));
                log.debug("Inbound TLS handshake succeeded routeId={} sessionId={}", route.id(), sessionId);
            } else {
                sessionStore.recordLifecycleAsync(sessionId, "TLS_INBOUND_FAILED", Map.of("error", handshakeEvent.cause().toString()));
                log.warn("Inbound TLS handshake failed routeId={} sessionId={} error={}",
                        route.id(), sessionId, handshakeEvent.cause().toString());
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
        private final RouteConfig route;
        private final SessionStore sessionStore;
        private final SslContext outboundSslContext;

        private OutboundInitializer(Channel inboundChannel, String sessionId, ProxyConfig config, RouteConfig route, SessionStore sessionStore, SslContext outboundSslContext) {
            this.inboundChannel = inboundChannel;
            this.sessionId = sessionId;
            this.config = config;
            this.route = route;
            this.sessionStore = sessionStore;
            this.outboundSslContext = outboundSslContext;
        }

        @Override
        protected void initChannel(io.netty.channel.socket.SocketChannel channel) {
            if (outboundSslContext != null) {
                channel.pipeline().addLast("ssl", TlsContextFactory.newClientHandler(route, outboundSslContext, channel.alloc()));
            }
            channel.pipeline().addLast("backend", new BackendHandler(inboundChannel, sessionId, config, route, sessionStore));
        }
    }
}
