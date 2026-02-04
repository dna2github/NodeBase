package seven.lab.wstun.server;

import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.handler.codec.http.DefaultHttpContent;

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
import seven.lab.wstun.config.ServerConfig;
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
    private final ServerConfig serverConfig;

    private WebSocketServerHandshaker handshaker;

    // Static reference for relay responses
    private static String staticCorsOrigins = "*";
    
    // Static reference for server config (for WebSocket handler)
    private static ServerConfig staticServerConfig;
    
    // Track channels with active streaming responses
    private static final Map<String, ChannelHandlerContext> streamingResponses = new ConcurrentHashMap<>();

    public HttpHandler(ServiceManager serviceManager, RequestManager requestManager, 
                      LocalServiceManager localServiceManager, boolean ssl, String corsOrigins, int port,
                      ServerConfig serverConfig) {
        this.serviceManager = serviceManager;
        this.requestManager = requestManager;
        this.localServiceManager = localServiceManager;
        this.ssl = ssl;
        this.corsOrigins = corsOrigins != null ? corsOrigins : "*";
        this.port = port;
        this.serverConfig = serverConfig;
        staticCorsOrigins = this.corsOrigins;
        staticServerConfig = serverConfig;
    }

    public HttpHandler(ServiceManager serviceManager, RequestManager requestManager, boolean ssl) {
        this(serviceManager, requestManager, null, ssl, "*", 8080, null);
    }
    
    public static ServerConfig getServerConfig() {
        return staticServerConfig;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        Log.d(TAG, "HTTP Request: " + request.method() + " " + request.uri());
        try {
            // Check for WebSocket upgrade
            if (isWebSocketUpgrade(request)) {
                handleWebSocketUpgrade(ctx, request);
                return;
            }

            // Handle HTTP request
            handleHttpRequest(ctx, request);
        } catch (Exception e) {
            Log.e(TAG, "Error handling HTTP request", e);
            try {
                sendInternalServerError(ctx, request, e.getMessage());
            } catch (Exception e2) {
                Log.e(TAG, "Error sending error response", e2);
                ctx.close();
            }
        }
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

        // Only log at INFO level to reduce overhead
        // Log.d(TAG, "HTTP " + request.method() + " " + path);

        // Handle CORS preflight
        if (request.method() == HttpMethod.OPTIONS) {
            sendCorsPreflightResponse(ctx, request);
            return;
        }
        
        // Check server auth (skip for libwstun.js which is public)
        if (!"/libwstun.js".equals(path) && !validateServerAuth(request)) {
            sendUnauthorized(ctx, request, "Invalid or missing server auth token");
            return;
        }

        // Root path - show server info
        if ("/".equals(path)) {
            sendServerInfo(ctx, request);
            return;
        }
        
        // Admin/management page
        if ("/admin".equals(path) || "/admin/".equals(path)) {
            sendAdminPage(ctx, request);
            return;
        }
        
        // Debug logs endpoint - streams logcat (only if enabled in config)
        if ("/debug/logs".equals(path)) {
            if (serverConfig != null && serverConfig.isDebugLogsEnabled()) {
                handleDebugLogs(ctx, request);
            } else {
                sendNotFound(ctx, request);
            }
            return;
        }
        
        // Serve libwstun.js library (public, no auth)
        if ("/libwstun.js".equals(path)) {
            sendLibWstun(ctx, request);
            return;
        }

        // Parse service name from path
        String[] pathParts = path.split("/");
        if (pathParts.length < 2) {
            sendNotFound(ctx, request);
            return;
        }

        String serviceName = pathParts[1];
        
        // Handle marketplace and service management API
        if ("_api".equals(serviceName)) {
            handleApiRequest(ctx, request, pathParts);
            return;
        }
        
        // Check for local/installed service endpoints
        if (localServiceManager != null) {
            seven.lab.wstun.marketplace.InstalledService installedSvc = localServiceManager.getInstalledService(serviceName);
            if (installedSvc != null) {
                if (handleLocalService(ctx, request, serviceName, pathParts)) {
                    return;
                }
            }
        }
        
        // Check for file download requests (/fileshare/download/{fileId})
        if ("fileshare".equals(serviceName) && pathParts.length >= 4 && "download".equals(pathParts[2])) {
            handleFileDownload(ctx, request, pathParts);
            return;
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
        httpResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        
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
            if (html == null) {
                sendNotFound(ctx, request);
            } else {
                sendHtmlResponse(ctx, request, html);
            }
            return true;
        }
        
        // /main - Main service UI (serve directly when service is registered)
        if ("main".equals(subPath)) {
            // Serve the user client HTML directly from assets (regardless of service running state)
            String html = localServiceManager.getServiceMainHtml(serviceName);
            if (html != null) {
                sendHtmlResponse(ctx, request, html);
                return true;
            }
            
            // Fallback: send not found
            sendNotFound(ctx, request);
            return true;
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
        
        // GET /service/api/clients - List connected clients
        if ("clients".equals(apiAction) && request.method() == HttpMethod.GET) {
            java.util.List<ServiceManager.ClientInfo> clients = serviceManager.getClientsByType(serviceName);
            com.google.gson.JsonArray clientsArray = new com.google.gson.JsonArray();
            for (ServiceManager.ClientInfo client : clients) {
                JsonObject clientObj = new JsonObject();
                clientObj.addProperty("userId", client.getUserId());
                clientObj.addProperty("clientType", client.getClientType());
                clientObj.addProperty("connectedAt", client.getConnectedAt());
                clientObj.addProperty("connected", client.getChannel() != null && client.getChannel().isActive());
                clientsArray.add(clientObj);
            }
            JsonObject response = new JsonObject();
            response.add("clients", clientsArray);
            response.addProperty("count", clients.size());
            sendJsonResponse(ctx, request, response.toString());
            return true;
        }
        
        // POST /service/api/kick - Kick a client
        if ("kick".equals(apiAction) && request.method() == HttpMethod.POST) {
            String userId = null;
            ByteBuf content = request.content();
            if (content.readableBytes() > 0) {
                byte[] bodyBytes = new byte[content.readableBytes()];
                content.readBytes(bodyBytes);
                try {
                    JsonObject body = gson.fromJson(new String(bodyBytes, StandardCharsets.UTF_8), JsonObject.class);
                    if (body.has("userId")) {
                        userId = body.get("userId").getAsString();
                    }
                } catch (Exception e) {
                    // Ignore parse errors
                }
            }
            
            JsonObject response = new JsonObject();
            if (userId == null || userId.isEmpty()) {
                response.addProperty("success", false);
                response.addProperty("error", "userId is required");
            } else {
                boolean success = serviceManager.kickClient(userId);
                response.addProperty("success", success);
                if (!success) {
                    response.addProperty("error", "Client not found: " + userId);
                }
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
     * Handle API requests (/_api/...).
     */
    private void handleApiRequest(ChannelHandlerContext ctx, FullHttpRequest request, String[] pathParts) {
        if (pathParts.length < 3) {
            sendJsonResponse(ctx, request, "{\"error\": \"Invalid API path\"}");
            return;
        }
        
        String apiCategory = pathParts[2];
        
        // /_api/services - Service management
        if ("services".equals(apiCategory)) {
            handleServicesApi(ctx, request, pathParts);
            return;
        }
        
        // /_api/instances - Instance management
        if ("instances".equals(apiCategory)) {
            handleInstancesApi(ctx, request, pathParts);
            return;
        }
        
        // /_api/marketplace - Marketplace operations
        if ("marketplace".equals(apiCategory)) {
            handleMarketplaceApi(ctx, request, pathParts);
            return;
        }
        
        sendJsonResponse(ctx, request, "{\"error\": \"Unknown API category\"}");
    }
    
    /**
     * Handle /_api/services endpoints.
     */
    private void handleServicesApi(ChannelHandlerContext ctx, FullHttpRequest request, String[] pathParts) {
        // GET /_api/services - List all installed services
        if (pathParts.length == 3 && request.method() == HttpMethod.GET) {
            if (localServiceManager != null) {
                JsonObject response = new JsonObject();
                response.add("services", localServiceManager.getInstalledServicesJson());
                sendJsonResponse(ctx, request, response.toString());
            } else {
                sendJsonResponse(ctx, request, "{\"services\": []}");
            }
            return;
        }
        
        // /_api/services/{name}/...
        if (pathParts.length >= 4) {
            String serviceName = pathParts[3];
            
            // GET /_api/services/{name} - Get service details
            if (pathParts.length == 4 && request.method() == HttpMethod.GET) {
                seven.lab.wstun.marketplace.InstalledService service = 
                    localServiceManager != null ? localServiceManager.getInstalledService(serviceName) : null;
                if (service != null) {
                    JsonObject obj = service.toJson();
                    obj.addProperty("name", serviceName);
                    obj.addProperty("instanceCount", serviceManager.getInstanceCountForService(serviceName));
                    sendJsonResponse(ctx, request, obj.toString());
                } else {
                    sendJsonResponse(ctx, request, "{\"error\": \"Service not found\"}");
                }
                return;
            }
            
            // POST /_api/services/{name}/enable
            if (pathParts.length == 5 && "enable".equals(pathParts[4]) && request.method() == HttpMethod.POST) {
                boolean success = localServiceManager != null && localServiceManager.enableService(serviceName);
                JsonObject response = new JsonObject();
                response.addProperty("success", success);
                if (!success) {
                    response.addProperty("error", "Failed to enable service");
                }
                sendJsonResponse(ctx, request, response.toString());
                return;
            }
            
            // POST /_api/services/{name}/disable
            if (pathParts.length == 5 && "disable".equals(pathParts[4]) && request.method() == HttpMethod.POST) {
                boolean success = localServiceManager != null && 
                    localServiceManager.disableService(serviceName, serviceManager);
                JsonObject response = new JsonObject();
                response.addProperty("success", success);
                if (!success) {
                    response.addProperty("error", "Failed to disable service");
                }
                sendJsonResponse(ctx, request, response.toString());
                return;
            }
            
            // DELETE /_api/services/{name} - Uninstall service
            if (pathParts.length == 4 && request.method() == HttpMethod.DELETE) {
                boolean success = localServiceManager != null && 
                    localServiceManager.uninstallService(serviceName, serviceManager);
                JsonObject response = new JsonObject();
                response.addProperty("success", success);
                if (!success) {
                    response.addProperty("error", "Failed to uninstall service (may be built-in)");
                }
                sendJsonResponse(ctx, request, response.toString());
                return;
            }
        }
        
        sendJsonResponse(ctx, request, "{\"error\": \"Invalid services API path\"}");
    }
    
    /**
     * Handle /_api/instances endpoints.
     */
    private void handleInstancesApi(ChannelHandlerContext ctx, FullHttpRequest request, String[] pathParts) {
        // GET /_api/instances - List all running instances
        if (pathParts.length == 3 && request.method() == HttpMethod.GET) {
            com.google.gson.JsonArray instancesArr = new com.google.gson.JsonArray();
            for (ServiceManager.ServiceInstance instance : getAllInstances()) {
                instancesArr.add(instance.toJson());
            }
            JsonObject response = new JsonObject();
            response.add("instances", instancesArr);
            sendJsonResponse(ctx, request, response.toString());
            return;
        }
        
        // GET /_api/instances/{service} - List instances for a service
        if (pathParts.length == 4 && request.method() == HttpMethod.GET) {
            String serviceName = pathParts[3];
            com.google.gson.JsonArray instancesArr = new com.google.gson.JsonArray();
            for (ServiceManager.ServiceInstance instance : serviceManager.getInstancesForService(serviceName)) {
                instancesArr.add(instance.toJson());
            }
            JsonObject response = new JsonObject();
            response.add("instances", instancesArr);
            sendJsonResponse(ctx, request, response.toString());
            return;
        }
        
        sendJsonResponse(ctx, request, "{\"error\": \"Invalid instances API path\"}");
    }
    
    /**
     * Get all instances across all services.
     */
    private java.util.List<ServiceManager.ServiceInstance> getAllInstances() {
        java.util.List<ServiceManager.ServiceInstance> all = new java.util.ArrayList<>();
        if (localServiceManager != null) {
            for (String serviceName : localServiceManager.getInstalledServices().keySet()) {
                all.addAll(serviceManager.getInstancesForService(serviceName));
            }
        }
        return all;
    }
    
    /**
     * Handle /_api/marketplace endpoints.
     */
    private void handleMarketplaceApi(ChannelHandlerContext ctx, FullHttpRequest request, String[] pathParts) {
        if (localServiceManager == null) {
            sendJsonResponse(ctx, request, "{\"error\": \"Marketplace not available\"}");
            return;
        }
        
        seven.lab.wstun.marketplace.MarketplaceService marketplace = 
            localServiceManager.getMarketplaceService();
        
        // GET /_api/marketplace/url - Get current marketplace URL
        if (pathParts.length == 4 && "url".equals(pathParts[3]) && request.method() == HttpMethod.GET) {
            JsonObject response = new JsonObject();
            response.addProperty("url", marketplace.getMarketplaceUrl());
            sendJsonResponse(ctx, request, response.toString());
            return;
        }
        
        // POST /_api/marketplace/url - Set marketplace URL
        if (pathParts.length == 4 && "url".equals(pathParts[3]) && request.method() == HttpMethod.POST) {
            String url = getRequestBodyString(request, "url");
            if (url != null) {
                marketplace.setMarketplaceUrl(url);
            }
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("url", marketplace.getMarketplaceUrl());
            sendJsonResponse(ctx, request, response.toString());
            return;
        }
        
        // POST /_api/marketplace/list - List services from marketplace
        if (pathParts.length == 4 && "list".equals(pathParts[3]) && request.method() == HttpMethod.POST) {
            String url = getRequestBodyString(request, "url");
            if (url == null || url.isEmpty()) {
                url = marketplace.getMarketplaceUrl();
            }
            if (url == null || url.isEmpty()) {
                sendJsonResponse(ctx, request, "{\"error\": \"No marketplace URL specified\"}");
                return;
            }
            
            final String marketplaceUrl = url;
            marketplace.listMarketplace(marketplaceUrl, 
                new seven.lab.wstun.marketplace.MarketplaceService.MarketplaceCallback<java.util.List<JsonObject>>() {
                    @Override
                    public void onSuccess(java.util.List<JsonObject> result) {
                        ctx.executor().execute(() -> {
                            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                            for (JsonObject svc : result) {
                                // Add installed status
                                String name = svc.has("name") ? svc.get("name").getAsString() : null;
                                if (name != null) {
                                    svc.addProperty("installed", marketplace.isInstalled(name));
                                }
                                arr.add(svc);
                            }
                            JsonObject response = new JsonObject();
                            response.add("services", arr);
                            response.addProperty("url", marketplaceUrl);
                            sendJsonResponse(ctx, request, response.toString());
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        ctx.executor().execute(() -> {
                            JsonObject response = new JsonObject();
                            response.addProperty("error", error);
                            sendJsonResponse(ctx, request, response.toString());
                        });
                    }
                });
            return;
        }
        
        // POST /_api/marketplace/install - Install a service
        if (pathParts.length == 4 && "install".equals(pathParts[3]) && request.method() == HttpMethod.POST) {
            String url = getRequestBodyString(request, "url");
            String name = getRequestBodyString(request, "name");
            
            if (url == null || url.isEmpty()) {
                url = marketplace.getMarketplaceUrl();
            }
            if (name == null || name.isEmpty()) {
                sendJsonResponse(ctx, request, "{\"error\": \"Service name required\"}");
                return;
            }
            
            final String marketplaceUrl = url;
            final String serviceName = name;
            
            marketplace.installService(marketplaceUrl, serviceName,
                new seven.lab.wstun.marketplace.MarketplaceService.MarketplaceCallback<seven.lab.wstun.marketplace.InstalledService>() {
                    @Override
                    public void onSuccess(seven.lab.wstun.marketplace.InstalledService result) {
                        ctx.executor().execute(() -> {
                            JsonObject response = new JsonObject();
                            response.addProperty("success", true);
                            response.add("service", result.toJson());
                            sendJsonResponse(ctx, request, response.toString());
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        ctx.executor().execute(() -> {
                            JsonObject response = new JsonObject();
                            response.addProperty("success", false);
                            response.addProperty("error", error);
                            sendJsonResponse(ctx, request, response.toString());
                        });
                    }
                });
            return;
        }
        
        sendJsonResponse(ctx, request, "{\"error\": \"Invalid marketplace API path\"}");
    }
    
    /**
     * Helper to get a string value from request JSON body.
     */
    private String getRequestBodyString(FullHttpRequest request, String key) {
        ByteBuf content = request.content();
        if (content.readableBytes() > 0) {
            byte[] bodyBytes = new byte[content.readableBytes()];
            content.getBytes(content.readerIndex(), bodyBytes);
            try {
                JsonObject body = gson.fromJson(new String(bodyBytes, StandardCharsets.UTF_8), JsonObject.class);
                if (body.has(key) && !body.get(key).isJsonNull()) {
                    return body.get(key).getAsString();
                }
            } catch (Exception e) {
                // Ignore parse errors
            }
        }
        return null;
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
        html.append(".status.disabled{background:#f5f5f5;color:#9e9e9e;}");
        html.append(".admin-btn{display:inline-block;padding:10px 20px;background:#6200ee;color:white;border-radius:6px;margin-top:10px;}</style>");
        html.append("</head><body><div class='container'>");
        html.append("<h1>WSTun Server</h1>");
        html.append("<p><a href='/admin' class='admin-btn'>Service Manager</a></p>");
        
        // Installed services section
        if (localServiceManager != null) {
            html.append("<h2>Installed Services</h2>");
            html.append("<ul>");
            
            for (java.util.Map.Entry<String, seven.lab.wstun.marketplace.InstalledService> entry : 
                    localServiceManager.getInstalledServices().entrySet()) {
                String name = entry.getKey();
                seven.lab.wstun.marketplace.InstalledService svc = entry.getValue();
                String displayName = svc.getDisplayName();
                boolean enabled = svc.isEnabled();
                int instanceCount = serviceManager.getInstanceCountForService(name);
                
                html.append("<li><strong>").append(displayName).append("</strong>");
                
                if (enabled) {
                    html.append("<span class='status running'>Enabled</span>");
                    if (instanceCount > 0) {
                        html.append("<span class='status running'>").append(instanceCount).append(" instances</span>");
                    }
                    html.append(" - <a href='/").append(name).append("/service'>Manage</a>");
                } else {
                    html.append("<span class='status disabled'>Disabled</span>");
                }
                
                html.append("</li>");
            }
            
            if (localServiceManager.getInstalledServices().isEmpty()) {
                html.append("<li>No services installed</li>");
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
        if (html == null) {
            sendNotFound(ctx, request);
            return;
        }
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
            Unpooled.wrappedBuffer("Not Found".getBytes())
        );
        
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 9);
        
        sendResponse(ctx, request, response);
    }
    
    private void sendUnauthorized(ChannelHandlerContext ctx, FullHttpRequest request, String message) {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.UNAUTHORIZED,
            Unpooled.wrappedBuffer(bytes)
        );
        
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        response.headers().set(HttpHeaderNames.WWW_AUTHENTICATE, "Bearer realm=\"wstun\"");
        
        sendResponse(ctx, request, response);
    }
    
    /**
     * Validate server-level authentication.
     * Checks Authorization header or ?token query parameter.
     */
    private boolean validateServerAuth(FullHttpRequest request) {
        if (serverConfig == null || !serverConfig.isAuthEnabled()) {
            return true;
        }
        
        String token = null;
        
        // Check Authorization header (Bearer token)
        String authHeader = request.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        
        // Check query parameter as fallback
        if (token == null) {
            QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
            if (decoder.parameters().containsKey("token")) {
                token = decoder.parameters().get("token").get(0);
            }
        }
        
        return serverConfig.validateServerAuth(token);
    }
    
    /**
     * Serve the admin page.
     */
    private void sendAdminPage(ChannelHandlerContext ctx, FullHttpRequest request) {
        String html = localServiceManager != null ? localServiceManager.getAdminHtml() : null;
        if (html == null) {
            sendNotFound(ctx, request);
            return;
        }
        sendHtmlResponse(ctx, request, html);
    }
    
    /**
     * Serve the libwstun.js library.
     */
    private void sendLibWstun(ChannelHandlerContext ctx, FullHttpRequest request) {
        String js = localServiceManager != null ? localServiceManager.getLibWstunJs() : null;
        if (js == null) {
            sendNotFound(ctx, request);
            return;
        }
        
        byte[] bytes = js.getBytes(StandardCharsets.UTF_8);
        
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(bytes)
        );
        
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/javascript; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "public, max-age=3600");
        
        sendResponse(ctx, request, response);
    }
    
    /**
     * Handle debug logs endpoint - streams logcat to the browser.
     */
    private void handleDebugLogs(ChannelHandlerContext ctx, FullHttpRequest request) {
        // Send initial response with chunked transfer encoding
        HttpResponse response = new DefaultHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK
        );
        
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, corsOrigins);
        
        ctx.writeAndFlush(response);
        
        // Start a thread to stream logcat
        Thread logThread = new Thread(() -> {
            Process process = null;
            BufferedReader reader = null;
            try {
                // Start logcat process - filter to show Info and above, with timestamp
                process = Runtime.getRuntime().exec("logcat -v time *:I");
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                
                String line;
                while (ctx.channel().isActive() && (line = reader.readLine()) != null) {
                    // Send each log line as a chunk
                    String chunk = line + "\n";
                    ctx.writeAndFlush(new DefaultHttpContent(
                        Unpooled.copiedBuffer(chunk, StandardCharsets.UTF_8)));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error streaming logcat", e);
            } finally {
                // Clean up
                if (reader != null) {
                    try { reader.close(); } catch (Exception ignored) {}
                }
                if (process != null) {
                    process.destroy();
                }
                
                // Send end marker and close
                if (ctx.channel().isActive()) {
                    ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                       .addListener(ChannelFutureListener.CLOSE);
                }
            }
        });
        logThread.setName("LogcatStreamer");
        logThread.setDaemon(true);
        logThread.start();
    }
    
    private void sendServiceUnavailable(ChannelHandlerContext ctx, FullHttpRequest request) {
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.SERVICE_UNAVAILABLE,
            Unpooled.wrappedBuffer("Service Unavailable".getBytes())
        );
        
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 19);
        
        sendResponse(ctx, request, response);
    }
    
    private void sendInternalServerError(ChannelHandlerContext ctx, FullHttpRequest request, String message) {
        String body = "Internal Server Error: " + (message != null ? message : "Unknown error");
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.INTERNAL_SERVER_ERROR,
            Unpooled.wrappedBuffer(body.getBytes(StandardCharsets.UTF_8))
        );
        
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, body.getBytes(StandardCharsets.UTF_8).length);
        
        sendResponse(ctx, request, response);
    }

    private void sendResponse(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
        // Add CORS headers to all responses
        addCorsHeaders(response);
        
        // Ensure Content-Length is set - this is critical for keep-alive to work properly
        if (!response.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        }
        
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        
        if (keepAlive) {
            // For keep-alive, set the header and don't close
            if (!response.headers().contains(HttpHeaderNames.CONNECTION)) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }
        } else {
            // For non-keep-alive, set Connection: close
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        }
        
        // Write and flush the response
        ctx.writeAndFlush(response).addListener(f -> {
            if (!keepAlive) {
                ctx.close();
            }
        });
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
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Log.d(TAG, "Channel active: " + ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Log.d(TAG, "Channel inactive: " + ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Log.e(TAG, "HTTP handler error", cause);
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof io.netty.handler.timeout.IdleStateEvent) {
            Log.d(TAG, "Idle timeout, closing connection");
            ctx.close();
        } else {
            super.userEventTriggered(ctx, evt);
        }
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

        // Flush the headers immediately so the client knows the response has started
        ctx.writeAndFlush(response);
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
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
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
            Unpooled.wrappedBuffer("File not found".getBytes())
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 14);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        addStaticCorsHeaders(response);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void sendServiceUnavailableResponse(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.SERVICE_UNAVAILABLE,
            Unpooled.wrappedBuffer("File owner not connected".getBytes())
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 24);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        addStaticCorsHeaders(response);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
    
    /**
     * Handle file download request - streams file from owner client.
     */
    private void handleFileDownload(ChannelHandlerContext ctx, FullHttpRequest request, String[] pathParts) {
        // Extract file ID from path: /fileshare/download/{fileId}
        String fileId;
        try {
            fileId = java.net.URLDecoder.decode(pathParts[3], "UTF-8");
        } catch (Exception e) {
            fileId = pathParts[3];
        }
        
        Log.d(TAG, "File download request: " + fileId);
        
        // Look up file in registry
        ServiceManager.FileInfo file = serviceManager.getFile(fileId);
        if (file == null) {
            sendNotFoundResponse(ctx);
            return;
        }
        
        // Check if owner is connected
        Channel ownerChannel = file.getOwnerChannel();
        if (ownerChannel == null || !ownerChannel.isActive()) {
            sendServiceUnavailableResponse(ctx);
            return;
        }
        
        // Create pending request for streaming response
        String requestId = String.valueOf(System.currentTimeMillis()) + "-" + (int)(Math.random() * 10000);
        PendingRequest pending = new PendingRequest(requestId, ctx, request, "fileshare");
        requestManager.addPendingRequest(pending);
        
        // Send file_request to owner
        Message message = new Message(Message.TYPE_FILE_REQUEST);
        JsonObject payload = new JsonObject();
        payload.addProperty("requestId", requestId);
        payload.addProperty("fileId", fileId);
        message.setPayload(payload);
        
        ownerChannel.writeAndFlush(new TextWebSocketFrame(message.toJson()));
        Log.d(TAG, "Sent file request to owner for: " + file.getFilename());
    }
}
