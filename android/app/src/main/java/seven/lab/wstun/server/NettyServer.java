package seven.lab.wstun.server;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
import io.netty.util.concurrent.DefaultThreadFactory;
import seven.lab.wstun.config.ServerConfig;

/**
 * Netty-based HTTP/WebSocket server with optional HTTPS support.
 * Uses dedicated threads with high priority to ensure responsiveness
 * even when the Android app is in the background.
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
    private PowerManager.WakeLock wakeLock;

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

        // Acquire a partial wake lock to keep the CPU running for network I/O
        // This is essential for the server to respond when the app is in the background
        try {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "WSTun:NettyServer"
                );
                wakeLock.acquire();
                Log.i(TAG, "Acquired wake lock for background operation");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to acquire wake lock: " + e.getMessage());
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
        final ServerConfig serverConfig = config;

        // Use custom thread factory to create high-priority daemon threads
        // This ensures Netty threads keep running even when app is backgrounded
        DefaultThreadFactory bossThreadFactory = new DefaultThreadFactory("wstun-boss", true, Thread.MAX_PRIORITY);
        DefaultThreadFactory workerThreadFactory = new DefaultThreadFactory("wstun-worker", true, Thread.MAX_PRIORITY);
        
        bossGroup = new NioEventLoopGroup(1, bossThreadFactory);
        // Use more worker threads to handle concurrent HTTP and WebSocket connections
        workerGroup = new NioEventLoopGroup(Math.max(4, Runtime.getRuntime().availableProcessors() * 2), workerThreadFactory);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .option(ChannelOption.SO_BACKLOG, 1024)
            .option(ChannelOption.SO_REUSEADDR, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    Log.d(TAG, "New connection from: " + ch.remoteAddress());
                    ChannelPipeline pipeline = ch.pipeline();

                    // SSL handler
                    if (finalSslContext != null) {
                        pipeline.addLast("ssl", finalSslContext.newHandler(ch.alloc()));
                    }

                    // HTTP codec
                    pipeline.addLast("http-codec", new HttpServerCodec());
                    
                    // Aggregate HTTP message parts into FullHttpRequest
                    pipeline.addLast("http-aggregator", new HttpObjectAggregator(65536));

                    // Idle state handler - longer timeouts for better stability
                    // Read idle: 120s, Write idle: 60s, All idle: 0 (disabled)
                    pipeline.addLast("idle", new IdleStateHandler(120, 60, 0));

                    // HTTP/WebSocket handler with CORS configuration and local service support
                    pipeline.addLast("http-handler", 
                        new HttpHandler(serviceManager, requestManager, localSvcMgr, ssl, corsOrigins, serverPort, serverConfig));
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

        // Shutdown event loops with timeout
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }

        // Release wake lock
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
                Log.i(TAG, "Released wake lock");
            } catch (Exception e) {
                Log.w(TAG, "Failed to release wake lock: " + e.getMessage());
            }
            wakeLock = null;
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

    /**
     * Get the local service manager.
     */
    public LocalServiceManager getLocalServiceManager() {
        return localServiceManager;
    }
}
