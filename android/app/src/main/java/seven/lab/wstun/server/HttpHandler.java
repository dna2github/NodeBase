package seven.lab.wstun.server;

import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import seven.lab.wstun.protocol.HttpRelayRequest;
import seven.lab.wstun.protocol.HttpRelayResponse;
import seven.lab.wstun.protocol.Message;

/**
 * HTTP request handler that routes requests to registered services.
 */
public class HttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    
    private static final String TAG = "HttpHandler";
    private static final Gson gson = new Gson();

    private final ServiceManager serviceManager;
    private final RequestManager requestManager;
    private final LocalServiceManager localServiceManager;
    private final boolean ssl;
    private final String corsOrigins;
    private final int port;

    private WebSocketServerHandshaker handshaker;

    // Static reference for relay responses
    private static String staticCorsOrigins = "*";
    
    // Track channels with active streaming responses
    private static final Map<String, ChannelHandlerContext> streamingResponses = new ConcurrentHashMap<>();

    public HttpHandler(ServiceManager serviceManager, RequestManager requestManager, 
                      LocalServiceManager localServiceManager, boolean ssl, String corsOrigins, int port) {
        this.serviceManager = serviceManager;
        this.requestManager = requestManager;
        this.localServiceManager = localServiceManager;
        this.ssl = ssl;
        this.corsOrigins = corsOrigins != null ? corsOrigins : "*";
        this.port = port;
        staticCorsOrigins = this.corsOrigins;
    }

    public HttpHandler(ServiceManager serviceManager, RequestManager requestManager, boolean ssl) {
        this(serviceManager, requestManager, null, ssl, "*", 8080);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        // Check for WebSocket upgrade
        if (isWebSocketUpgrade(request)) {
            handleWebSocketUpgrade(ctx, request);
            return;
        }

        // Handle HTTP request
        handleHttpRequest(ctx, request);
    }

    private boolean isWebSocketUpgrade(FullHttpRequest request) {
        String upgrade = request.headers().get(HttpHeaderNames.UPGRADE);
        return "websocket".equalsIgnoreCase(upgrade);
    }

    private void handleWebSocketUpgrade(ChannelHandlerContext ctx, FullHttpRequest request) {
        String wsUrl = (ssl ? "wss" : "ws") + "://" + request.headers().get(HttpHeaderNames.HOST) + "/ws";
        
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
            wsUrl, null, true, 65536
        );
        
        handshaker = wsFactory.newHandshaker(request);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), request).addListener(future -> {
                if (future.isSuccess()) {
                    // Replace this handler with WebSocket handler
                    ctx.pipeline().replace(this, "websocket", 
                        new WebSocketHandler(serviceManager, requestManager));
                    Log.i(TAG, "WebSocket connection established");
                }
            });
        }
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        String uri = request.uri();
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        String path = decoder.path();

        Log.d(TAG, "HTTP " + request.method() + " " + path);

        // Handle CORS preflight
        if (request.method() == HttpMethod.OPTIONS) {
            sendCorsPreflightResponse(ctx, request);
            return;
        }

        // Root path - show server info
        if ("/".equals(path)) {
            sendServerInfo(ctx, request);
            return;
        }

        // Parse service name from path
        String[] pathParts = path.split("/");
        if (pathParts.length < 2) {
            sendNotFound(ctx, request);
            return;
        }

        String serviceName = pathParts[1];
        
        // Check for local service endpoints (/fileshare/service or /chat/service)
        if (localServiceManager != null && 
            ("fileshare".equals(serviceName) || "chat".equals(serviceName))) {
            if (handleLocalService(ctx, request, serviceName, pathParts)) {
                return;
            }
        }
        
        ServiceManager.ServiceEntry service = serviceManager.getService(serviceName);
        
        if (service == null) {
            sendNotFound(ctx, request);
            return;
        }

        // Check for static resources
        if (service.getRegistration().getStaticResources() != null) {
            String resourcePath = path.substring(serviceName.length() + 1);
            String content = service.getRegistration().getStaticResources().get(resourcePath);
            if (content != null) {
                sendStaticResource(ctx, request, content, resourcePath);
                return;
            }
        }

        // Relay request to service client
        relayRequest(ctx, request, service, path);
    }

    private void relayRequest(ChannelHandlerContext ctx, FullHttpRequest request, 
                              ServiceManager.ServiceEntry service, String path) {
        // Create relay request
        String requestId = String.valueOf(System.currentTimeMillis()) + "-" + (int)(Math.random() * 10000);
        
        HttpRelayRequest relayRequest = new HttpRelayRequest();
        relayRequest.setRequestId(requestId);
        relayRequest.setMethod(request.method().name());
        relayRequest.setPath(path);
        
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        relayRequest.setQuery(request.uri().contains("?") ? 
            request.uri().substring(request.uri().indexOf("?") + 1) : "");

        // Copy headers
        Map<String, String> headers = new HashMap<>();
        for (Map.Entry<String, String> entry : request.headers()) {
            headers.put(entry.getKey(), entry.getValue());
        }
        relayRequest.setHeaders(headers);

        // Copy body
        ByteBuf content = request.content();
        if (content.readableBytes() > 0) {
            byte[] bodyBytes = new byte[content.readableBytes()];
            content.readBytes(bodyBytes);
            
            // Check if binary
            String contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
            if (contentType != null && isBinaryContentType(contentType)) {
                relayRequest.setBodyBase64(Base64.encodeToString(bodyBytes, Base64.NO_WRAP));
            } else {
                relayRequest.setBody(new String(bodyBytes, StandardCharsets.UTF_8));
            }
        }

        // Store pending request
        PendingRequest pending = new PendingRequest(requestId, ctx, request, service.getName());
        requestManager.addPendingRequest(pending);

        // Send to service via WebSocket
        Message message = new Message(Message.TYPE_HTTP_REQUEST, service.getName());
        message.setPayload(Message.toPayload(relayRequest));
        
        if (service.getChannel() != null && service.getChannel().isActive()) {
            service.getChannel().writeAndFlush(new TextWebSocketFrame(message.toJson()));
        } else {
            requestManager.removePendingRequest(requestId);
            sendServiceUnavailable(ctx, request);
        }
    }

    private boolean isBinaryContentType(String contentType) {
        return contentType.startsWith("application/octet-stream") ||
               contentType.startsWith("image/") ||
               contentType.startsWith("audio/") ||
               contentType.startsWith("video/");
    }

    /**
     * Send relay response to HTTP client.
     */
    public static void sendRelayResponse(ChannelHandlerContext ctx, HttpRelayResponse response) {
        HttpResponseStatus status = HttpResponseStatus.valueOf(response.getStatus());
        
        byte[] body;
        if (response.getBodyBase64() != null) {
            body = Base64.decode(response.getBodyBase64(), Base64.NO_WRAP);
        } else if (response.getBody() != null) {
            body = response.getBody().getBytes(StandardCharsets.UTF_8);
        } else {
            body = new byte[0];
        }

        FullHttpResponse httpResponse = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            status,
            Unpooled.wrappedBuffer(body)
        );

        // Add CORS headers using static method
        addStaticCorsHeaders(httpResponse);

        // Copy headers
        if (response.getHeaders() != null) {
            for (Map.Entry<String, String> entry : response.getHeaders().entrySet()) {
                httpResponse.headers().set(entry.getKey(), entry.getValue());
            }
        }

        httpResponse.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, body.length);
        
        ctx.writeAndFlush(httpResponse).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Handle local service endpoints (/fileshare/service, /chat/service, etc.)
     * Returns true if the request was handled.
     */
    private boolean handleLocalService(ChannelHandlerContext ctx, FullHttpRequest request, 
                                       String serviceName, String[] pathParts) {
        String subPath = pathParts.length > 2 ? pathParts[2] : "";
        
        // Build server URL for generating links
        String host = request.headers().get(HttpHeaderNames.HOST);
        if (host == null) {
            host = "localhost:" + port;
        }
        String protocol = ssl ? "https" : "http";
        String serverUrl = protocol + "://" + host;
        
        // /service - Service management page
        if ("service".equals(subPath)) {
            // Check for API endpoints under /service/api/*
            if (pathParts.length > 3 && "api".equals(pathParts[3])) {
                return handleLocalServiceApi(ctx, request, serviceName, pathParts);
            }
            
            // Service management page
            String html = localServiceManager.getServicePageHtml(serviceName, serverUrl);
            sendHtmlResponse(ctx, request, html);
            return true;
        }
        
        // /main - Main service UI (only if service is running)
        if ("main".equals(subPath)) {
            LocalServiceManager.ServiceStatus status = localServiceManager.getServiceStatus(serviceName);
            if (status == null || !status.isRunning()) {
                sendServiceNotRunningResponse(ctx, request, serviceName, serverUrl);
                return true;
            }
            
            String html = localServiceManager.getServiceMainHtml(serviceName);
            if (html != null) {
                sendHtmlResponse(ctx, request, html);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Handle local service API endpoints.
     */
    private boolean handleLocalServiceApi(ChannelHandlerContext ctx, FullHttpRequest request,
                                          String serviceName, String[] pathParts) {
        if (pathParts.length < 5) {
            return false;
        }
        
        String apiAction = pathParts[4];
        
        // GET /service/api/status - Get service status
        if ("status".equals(apiAction) && request.method() == HttpMethod.GET) {
            LocalServiceManager.ServiceStatus status = localServiceManager.getServiceStatus(serviceName);
            if (status != null) {
                sendJsonResponse(ctx, request, status.toJson().toString());
            } else {
                sendJsonResponse(ctx, request, "{\"error\": \"Service not found\"}");
            }
            return true;
        }
        
        // POST /service/api/start - Start service
        if ("start".equals(apiAction) && request.method() == HttpMethod.POST) {
            boolean success = localServiceManager.startService(serviceName);
            LocalServiceManager.ServiceStatus status = localServiceManager.getServiceStatus(serviceName);
            JsonObject response = new JsonObject();
            response.addProperty("success", success);
            if (success && status != null) {
                response.addProperty("uuid", status.getUuid());
            } else if (!success) {
                response.addProperty("error", "Failed to start service");
            }
            sendJsonResponse(ctx, request, response.toString());
            return true;
        }
        
        // POST /service/api/stop - Stop service
        if ("stop".equals(apiAction) && request.method() == HttpMethod.POST) {
            // Parse request body for UUID
            String uuid = null;
            ByteBuf content = request.content();
            if (content.readableBytes() > 0) {
                byte[] bodyBytes = new byte[content.readableBytes()];
                content.readBytes(bodyBytes);
                try {
                    JsonObject body = gson.fromJson(new String(bodyBytes, StandardCharsets.UTF_8), JsonObject.class);
                    if (body.has("uuid")) {
                        uuid = body.get("uuid").getAsString();
                    }
                } catch (Exception e) {
                    // Ignore parse errors
                }
            }
            
            boolean success;
            if (uuid != null) {
                success = localServiceManager.stopServiceByUuid(serviceName, uuid);
            } else {
                success = localServiceManager.stopService(serviceName);
            }
            
            JsonObject response = new JsonObject();
            response.addProperty("success", success);
            if (!success) {
                response.addProperty("error", "Failed to stop service (UUID mismatch or service not running)");
            }
            sendJsonResponse(ctx, request, response.toString());
            return true;
        }
        
        return false;
    }
    
    /**
     * Send response when service is not running.
     */
    private void sendServiceNotRunningResponse(ChannelHandlerContext ctx, FullHttpRequest request,
                                               String serviceName, String serverUrl) {
        String displayName = "fileshare".equals(serviceName) ? "FileShare" : "Chat";
        String html = "<!DOCTYPE html>\n" +
            "<html><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
            "<title>" + displayName + " - Not Running</title>\n" +
            "<style>body{font-family:Arial,sans-serif;background:#f5f5f5;min-height:100vh;display:flex;align-items:center;justify-content:center;}\n" +
            ".card{background:white;padding:40px;border-radius:12px;box-shadow:0 2px 10px rgba(0,0,0,0.1);text-align:center;max-width:400px;}\n" +
            "h1{color:#ff9800;margin-bottom:16px;}p{color:#666;margin-bottom:20px;}\n" +
            "a{display:inline-block;padding:12px 24px;background:#6200ee;color:white;text-decoration:none;border-radius:8px;}\n" +
            "a:hover{background:#3700b3;}</style></head>\n" +
            "<body><div class=\"card\"><h1>Service Not Running</h1>\n" +
            "<p>The " + displayName + " service is not currently running.</p>\n" +
            "<a href=\"/" + serviceName + "/service\">Start Service</a></div></body></html>";
        sendHtmlResponse(ctx, request, html);
    }
    
    /**
     * Send JSON response.
     */
    private void sendJsonResponse(ChannelHandlerContext ctx, FullHttpRequest request, String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(bytes)
        );
        
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        
        sendResponse(ctx, request, response);
    }

    private void sendServerInfo(ChannelHandlerContext ctx, FullHttpRequest request) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head>");
        html.append("<meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<title>WSTun Server</title>");
        html.append("<style>body{font-family:Arial,sans-serif;margin:40px;background:#f5f5f5;}");
        html.append(".container{max-width:800px;margin:0 auto;background:white;padding:20px;border-radius:8px;box-shadow:0 2px 4px rgba(0,0,0,0.1);}");
        html.append("h1{color:#6200ee;}h2{margin-top:20px;color:#333;}ul{list-style:none;padding:0;}");
        html.append("li{padding:10px;margin:5px 0;background:#f9f9f9;border-radius:4px;}");
        html.append("a{color:#6200ee;text-decoration:none;}a:hover{text-decoration:underline;}");
        html.append(".status{display:inline-block;padding:2px 8px;border-radius:4px;font-size:12px;margin-left:8px;}");
        html.append(".status.running{background:#e8f5e9;color:#2e7d32;}");
        html.append(".status.stopped{background:#fff3e0;color:#e65100;}");
        html.append(".status.disabled{background:#f5f5f5;color:#9e9e9e;}</style>");
        html.append("</head><body><div class='container'>");
        html.append("<h1>WSTun Server</h1>");
        
        // Built-in services section
        if (localServiceManager != null) {
            html.append("<h2>Built-in Services</h2>");
            html.append("<ul>");
            
            // FileShare
            if (localServiceManager.isServiceEnabled("fileshare")) {
                LocalServiceManager.ServiceStatus fsStatus = localServiceManager.getServiceStatus("fileshare");
                html.append("<li><strong>FileShare</strong>");
                if (fsStatus != null && fsStatus.isRunning()) {
                    html.append("<span class='status running'>Running</span>");
                } else {
                    html.append("<span class='status stopped'>Stopped</span>");
                }
                html.append(" - <a href='/fileshare/service'>Manage</a>");
                if (fsStatus != null && fsStatus.isRunning()) {
                    html.append(" | <a href='/fileshare/main'>Open</a>");
                }
                html.append("</li>");
            } else {
                html.append("<li><strong>FileShare</strong><span class='status disabled'>Disabled</span></li>");
            }
            
            // Chat
            if (localServiceManager.isServiceEnabled("chat")) {
                LocalServiceManager.ServiceStatus chatStatus = localServiceManager.getServiceStatus("chat");
                html.append("<li><strong>Chat</strong>");
                if (chatStatus != null && chatStatus.isRunning()) {
                    html.append("<span class='status running'>Running</span>");
                } else {
                    html.append("<span class='status stopped'>Stopped</span>");
                }
                html.append(" - <a href='/chat/service'>Manage</a>");
                if (chatStatus != null && chatStatus.isRunning()) {
                    html.append(" | <a href='/chat/main'>Open</a>");
                }
                html.append("</li>");
            } else {
                html.append("<li><strong>Chat</strong><span class='status disabled'>Disabled</span></li>");
            }
            
            html.append("</ul>");
        }
        
        html.append("<h2>Registered Services</h2>");
        
        if (serviceManager.getServiceCount() == 0) {
            html.append("<p>No services registered</p>");
        } else {
            html.append("<ul>");
            for (ServiceManager.ServiceEntry service : serviceManager.getAllServices()) {
                html.append("<li><strong>").append(service.getName()).append("</strong>");
                html.append(" (").append(service.getType()).append(")");
                html.append(" - <a href='/").append(service.getName()).append("/main'>").append("/").append(service.getName()).append("/main</a>");
                html.append("</li>");
            }
            html.append("</ul>");
        }
        
        html.append("</div></body></html>");

        sendHtmlResponse(ctx, request, html.toString());
    }

    private void sendStaticResource(ChannelHandlerContext ctx, FullHttpRequest request, 
                                    String content, String path) {
        String contentType = "text/plain";
        if (path.endsWith(".html")) {
            contentType = "text/html; charset=UTF-8";
        } else if (path.endsWith(".js")) {
            contentType = "application/javascript";
        } else if (path.endsWith(".css")) {
            contentType = "text/css";
        } else if (path.endsWith(".json")) {
            contentType = "application/json";
        }

        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(bytes)
        );
        
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        
        sendResponse(ctx, request, response);
    }

    private void sendHtmlResponse(ChannelHandlerContext ctx, FullHttpRequest request, String html) {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(bytes)
        );
        
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        
        sendResponse(ctx, request, response);
    }

    private void sendNotFound(ChannelHandlerContext ctx, FullHttpRequest request) {
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.NOT_FOUND,
            Unpooled.copiedBuffer("Not Found".getBytes())
        );
        
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 9);
        
        sendResponse(ctx, request, response);
    }

    private void sendServiceUnavailable(ChannelHandlerContext ctx, FullHttpRequest request) {
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.SERVICE_UNAVAILABLE,
            Unpooled.copiedBuffer("Service Unavailable".getBytes())
        );
        
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 19);
        
        sendResponse(ctx, request, response);
    }

    private void sendResponse(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
        // Add CORS headers to all responses
        addCorsHeaders(response);
        
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            ctx.writeAndFlush(response);
        } else {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * Add CORS headers to response.
     */
    private void addCorsHeaders(FullHttpResponse response) {
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, corsOrigins);
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, OPTIONS");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Authorization, X-Requested-With");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE, "86400");
    }

    /**
     * Add CORS headers with static origin (for relay responses).
     */
    private static void addStaticCorsHeaders(FullHttpResponse response) {
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, staticCorsOrigins);
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, OPTIONS");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Authorization, X-Requested-With");
    }

    /**
     * Handle CORS preflight (OPTIONS) requests.
     */
    private void sendCorsPreflightResponse(ChannelHandlerContext ctx, FullHttpRequest request) {
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.EMPTY_BUFFER
        );
        
        addCorsHeaders(response);
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
        
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Log.e(TAG, "HTTP handler error", cause);
        ctx.close();
    }

    // ==================== Streaming Response Methods ====================

    /**
     * Start a streaming HTTP response (send headers, prepare for chunks).
     */
    public static void startStreamingResponse(ChannelHandlerContext ctx, String requestId, 
                                              int status, JsonObject headers) {
        HttpResponse response = new DefaultHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.valueOf(status)
        );

        // Add CORS headers
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, staticCorsOrigins);
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, OPTIONS");
        
        // Use chunked transfer encoding for streaming
        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        
        // Copy headers from payload
        if (headers != null) {
            for (String key : headers.keySet()) {
                // Skip Content-Length as we're using chunked encoding
                if (!key.equalsIgnoreCase("Content-Length")) {
                    response.headers().set(key, headers.get(key).getAsString());
                }
            }
        }

        ctx.write(response);
        streamingResponses.put(requestId, ctx);
        Log.d(TAG, "Started streaming response for: " + requestId);
    }

    /**
     * Send a chunk of streaming data.
     */
    public static void sendStreamingChunk(ChannelHandlerContext ctx, String chunkBase64) {
        if (ctx == null || !ctx.channel().isActive()) {
            return;
        }
        
        byte[] data = Base64.decode(chunkBase64, Base64.NO_WRAP);
        ByteBuf buf = Unpooled.wrappedBuffer(data);
        ctx.writeAndFlush(new io.netty.handler.codec.http.DefaultHttpContent(buf));
    }

    /**
     * End a streaming response.
     */
    public static void endStreamingResponse(ChannelHandlerContext ctx) {
        if (ctx == null || !ctx.channel().isActive()) {
            return;
        }
        
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
           .addListener(ChannelFutureListener.CLOSE);
        Log.d(TAG, "Ended streaming response");
    }

    /**
     * Send error and close streaming response.
     */
    public static void sendStreamingError(ChannelHandlerContext ctx, String error) {
        if (ctx == null || !ctx.channel().isActive()) {
            return;
        }
        
        // If headers not sent yet, send error response
        byte[] errorBytes = error.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.INTERNAL_SERVER_ERROR,
            Unpooled.wrappedBuffer(errorBytes)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, errorBytes.length);
        addStaticCorsHeaders(response);
        
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Request file stream from owner for relay download.
     */
    public static void requestFileStream(ServiceManager serviceManager, String requestId, 
                                         String fileId, ChannelHandlerContext httpCtx) {
        ServiceManager.FileInfo file = serviceManager.getFile(fileId);
        if (file == null) {
            sendNotFoundResponse(httpCtx);
            return;
        }

        Channel ownerChannel = file.getOwnerChannel();
        if (ownerChannel == null || !ownerChannel.isActive()) {
            sendServiceUnavailableResponse(httpCtx);
            return;
        }

        // Send file request to owner
        Message message = new Message(Message.TYPE_FILE_REQUEST);
        JsonObject payload = new JsonObject();
        payload.addProperty("requestId", requestId);
        payload.addProperty("fileId", fileId);
        message.setPayload(payload);
        
        ownerChannel.writeAndFlush(new TextWebSocketFrame(message.toJson()));
        Log.d(TAG, "Requested file stream from owner: " + fileId);
    }

    private static void sendNotFoundResponse(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.NOT_FOUND,
            Unpooled.copiedBuffer("File not found".getBytes())
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 14);
        addStaticCorsHeaders(response);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void sendServiceUnavailableResponse(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.SERVICE_UNAVAILABLE,
            Unpooled.copiedBuffer("File owner not connected".getBytes())
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 24);
        addStaticCorsHeaders(response);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
