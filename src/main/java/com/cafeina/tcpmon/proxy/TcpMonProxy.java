package com.cafeina.tcpmon.proxy;

import com.cafeina.tcpmon.ProxyConfig;
import com.cafeina.tcpmon.RouteConfig;
import com.cafeina.tcpmon.TransportMode;
import com.cafeina.tcpmon.session.SessionStore;
import com.cafeina.tcpmon.tls.TlsContextFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TcpMonProxy implements AutoCloseable {
    private final ProxyConfig config;
    private final RouteRegistry registry;
    private final SessionStore sessionStore;
    private final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    private final Map<String, Channel> channelsByRouteId = new ConcurrentHashMap<>();

    public TcpMonProxy(ProxyConfig config, RouteRegistry registry, SessionStore sessionStore) {
        this.config = config;
        this.registry = registry;
        this.sessionStore = sessionStore;
    }

    public void start() throws Exception {
        for (RouteConfig route : registry.routes()) {
            bindRoute(route);
        }
    }

    public void bindRoute(RouteConfig route) throws Exception {
        SslContext inboundTls = null;
        SslContext outboundTls = null;
        if (route.listener().transportMode() == TransportMode.TLS) {
            inboundTls = TlsContextFactory.buildServerContext(config, route);
        }
        if (route.target().transportMode() == TransportMode.TLS) {
            outboundTls = TlsContextFactory.buildClientContext(config, route);
        }
        final SslContext inboundTlsContext = inboundTls;
        final SslContext outboundTlsContext = outboundTls;

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        if (inboundTlsContext != null) {
                            channel.pipeline().addLast("ssl", inboundTlsContext.newHandler(channel.alloc()));
                        }
                        channel.pipeline().addLast("frontend", new FrontendHandler(config, route, sessionStore, outboundTlsContext));
                    }
                });

        channelsByRouteId.put(route.id(), bootstrap.bind(route.listener().host(), route.listener().port()).sync().channel());
    }

    public void unbindRoute(String routeId) {
        Channel ch = channelsByRouteId.remove(routeId);
        if (ch != null) ch.close().syncUninterruptibly();
    }

    @Override
    public void close() {
        for (Channel ch : channelsByRouteId.values()) {
            ch.close().syncUninterruptibly();
        }
        workerGroup.shutdownGracefully().syncUninterruptibly();
        bossGroup.shutdownGracefully().syncUninterruptibly();
    }
}
