package com.cafeina.tcpmon.proxy;

import com.cafeina.tcpmon.ProxyConfig;
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

public final class TcpMonProxy implements AutoCloseable {
    private final ProxyConfig config;
    private final SessionStore sessionStore;
    private final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    private Channel serverChannel;

    public TcpMonProxy(ProxyConfig config, SessionStore sessionStore) {
        this.config = config;
        this.sessionStore = sessionStore;
    }

    public void start() throws Exception {
        SslContext inboundTls = null;
        SslContext outboundTls = null;
        if (config.listener().transportMode() == TransportMode.TLS) {
            inboundTls = TlsContextFactory.buildServerContext(config);
        }
        if (config.target().transportMode() == TransportMode.TLS) {
            outboundTls = TlsContextFactory.buildClientContext(config);
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
                        channel.pipeline().addLast("frontend", new FrontendHandler(config, sessionStore, outboundTlsContext));
                    }
                });

        this.serverChannel = bootstrap.bind(config.listener().host(), config.listener().port()).sync().channel();
    }

    @Override
    public void close() {
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        workerGroup.shutdownGracefully().syncUninterruptibly();
        bossGroup.shutdownGracefully().syncUninterruptibly();
    }
}
