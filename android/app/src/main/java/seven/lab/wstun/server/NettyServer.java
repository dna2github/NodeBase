package seven.lab.wstun.server;

import android.content.Context;
import android.util.Log;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import seven.lab.wstun.config.ServerConfig;

/**
 * Netty-based HTTP/WebSocket server with optional HTTPS support.
 */
public class NettyServer {
    
    private static final String TAG = "NettyServer";

    private final Context context;
    private final ServerConfig config;
    private final ServiceManager serviceManager;
    private final RequestManager requestManager;
    private final LocalServiceManager localServiceManager;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private boolean running = false;

    public NettyServer(Context context, ServerConfig config, ServiceManager serviceManager) {
        this.context = context;
        this.config = config;
        this.serviceManager = serviceManager;
        this.requestManager = new RequestManager();
        this.localServiceManager = new LocalServiceManager(context, config);
        
        // Link ServiceManager to RequestManager for cleanup on disconnect
        this.serviceManager.setRequestManager(this.requestManager);
    }

    /**
     * Start the server.
     */
    public synchronized void start() throws Exception {
        if (running) {
            Log.w(TAG, "Server already running");
            return;
        }

        int port = config.getPort();
        boolean ssl = config.isHttpsEnabled();

        // Get SSL context if needed
        SslContext sslContext = null;
        if (ssl) {
            sslContext = SslContextFactory.getSslContext(context);
        }
        final SslContext finalSslContext = sslContext;
        final String corsOrigins = config.getCorsOrigins();
        final int serverPort = port;
        final LocalServiceManager localSvcMgr = localServiceManager;

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .option(ChannelOption.SO_BACKLOG, 128)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();

                    // SSL handler
                    if (finalSslContext != null) {
                        pipeline.addLast("ssl", finalSslContext.newHandler(ch.alloc()));
                    }

                    // HTTP codec
                    pipeline.addLast("http-codec", new HttpServerCodec());
                    pipeline.addLast("http-aggregator", new HttpObjectAggregator(65536));
                    pipeline.addLast("chunked-write", new ChunkedWriteHandler());

                    // Idle state handler
                    pipeline.addLast("idle", new IdleStateHandler(60, 30, 0));

                    // HTTP/WebSocket handler with CORS configuration and local service support
                    pipeline.addLast("http-handler", 
                        new HttpHandler(serviceManager, requestManager, localSvcMgr, ssl, corsOrigins, serverPort));
                }
            });

        serverChannel = bootstrap.bind(port).sync().channel();
        running = true;

        Log.i(TAG, "Server started on port " + port + (ssl ? " (HTTPS)" : " (HTTP)"));
    }

    /**
     * Stop the server.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;

        // Close all service connections
        serviceManager.clear();

        // Shutdown request manager
        requestManager.shutdown();

        // Close server channel
        if (serverChannel != null) {
            try {
                serverChannel.close().sync();
            } catch (InterruptedException e) {
                Log.e(TAG, "Error closing server channel", e);
            }
        }

        // Shutdown event loops
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }

        Log.i(TAG, "Server stopped");
    }

    /**
     * Check if server is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get the service manager.
     */
    public ServiceManager getServiceManager() {
        return serviceManager;
    }

    /**
     * Get the request manager.
     */
    public RequestManager getRequestManager() {
        return requestManager;
    }
}
