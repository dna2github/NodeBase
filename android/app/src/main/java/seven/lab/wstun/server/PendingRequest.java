package seven.lab.wstun.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

/**
 * Represents a pending HTTP request waiting for response from a service client.
 */
public class PendingRequest {
    
    private final String requestId;
    private final ChannelHandlerContext ctx;
    private final HttpRequest request;
    private final String serviceName;
    private final long timestamp;

    public PendingRequest(String requestId, ChannelHandlerContext ctx, HttpRequest request, String serviceName) {
        this.requestId = requestId;
        this.ctx = ctx;
        this.request = request;
        this.serviceName = serviceName;
        this.timestamp = System.currentTimeMillis();
    }

    public String getRequestId() {
        return requestId;
    }

    public ChannelHandlerContext getCtx() {
        return ctx;
    }

    public HttpRequest getRequest() {
        return request;
    }

    public String getServiceName() {
        return serviceName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isTimedOut(long timeoutMs) {
        return System.currentTimeMillis() - timestamp > timeoutMs;
    }
}
