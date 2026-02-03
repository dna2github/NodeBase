package seven.lab.wstun.server;

import android.util.Log;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

/**
 * Manages pending HTTP requests that are being relayed to service clients.
 */
public class RequestManager {
    
    private static final String TAG = "RequestManager";
    private static final long REQUEST_TIMEOUT_MS = 30000; // 30 seconds

    private final Map<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    public RequestManager() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::cleanupTimedOutRequests, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * Register a pending request.
     */
    public void addPendingRequest(PendingRequest request) {
        pendingRequests.put(request.getRequestId(), request);
        Log.d(TAG, "Added pending request: " + request.getRequestId());
    }

    /**
     * Get and remove a pending request.
     */
    public PendingRequest removePendingRequest(String requestId) {
        PendingRequest request = pendingRequests.remove(requestId);
        if (request != null) {
            Log.d(TAG, "Removed pending request: " + requestId);
        }
        return request;
    }

    /**
     * Get a pending request without removing it.
     */
    public PendingRequest getPendingRequest(String requestId) {
        return pendingRequests.get(requestId);
    }

    /**
     * Fail all pending requests for a specific service (when service disconnects).
     */
    public void failRequestsForService(String serviceName) {
        Iterator<Map.Entry<String, PendingRequest>> iterator = pendingRequests.entrySet().iterator();
        int count = 0;
        
        while (iterator.hasNext()) {
            Map.Entry<String, PendingRequest> entry = iterator.next();
            PendingRequest request = entry.getValue();
            
            if (serviceName.equals(request.getServiceName())) {
                iterator.remove();
                count++;
                
                // Send error response
                if (request.getCtx().channel().isActive()) {
                    DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1,
                        HttpResponseStatus.SERVICE_UNAVAILABLE,
                        Unpooled.copiedBuffer("Service disconnected".getBytes())
                    );
                    response.headers().set("Content-Type", "text/plain");
                    response.headers().setInt("Content-Length", 20);
                    request.getCtx().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                }
            }
        }
        
        if (count > 0) {
            Log.w(TAG, "Failed " + count + " pending requests for disconnected service: " + serviceName);
        }
    }

    /**
     * Clean up timed out requests.
     */
    private void cleanupTimedOutRequests() {
        Iterator<Map.Entry<String, PendingRequest>> iterator = pendingRequests.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, PendingRequest> entry = iterator.next();
            PendingRequest request = entry.getValue();
            
            if (request.isTimedOut(REQUEST_TIMEOUT_MS)) {
                iterator.remove();
                Log.w(TAG, "Request timed out: " + request.getRequestId());
                
                // Send timeout response
                if (request.getCtx().channel().isActive()) {
                    DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1,
                        HttpResponseStatus.GATEWAY_TIMEOUT,
                        Unpooled.copiedBuffer("Request timed out".getBytes())
                    );
                    request.getCtx().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                }
            }
        }
    }

    /**
     * Shutdown the request manager.
     */
    public void shutdown() {
        scheduler.shutdown();
        
        // Send error response to all pending requests
        for (PendingRequest request : pendingRequests.values()) {
            if (request.getCtx().channel().isActive()) {
                DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.SERVICE_UNAVAILABLE,
                    Unpooled.copiedBuffer("Server shutting down".getBytes())
                );
                request.getCtx().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            }
        }
        pendingRequests.clear();
    }
}
