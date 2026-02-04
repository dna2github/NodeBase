package seven.lab.wstun.server;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

import seven.lab.wstun.config.ServerConfig;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import seven.lab.wstun.protocol.HttpRelayResponse;
import seven.lab.wstun.protocol.Message;
import seven.lab.wstun.protocol.ServiceRegistration;

/**
 * Handler for WebSocket connections from service clients.
 */
public class WebSocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    
    private static final String TAG = "WebSocketHandler";
    private static final Gson gson = new Gson();

    private final ServiceManager serviceManager;
    private final RequestManager requestManager;

    public WebSocketHandler(ServiceManager serviceManager, RequestManager requestManager) {
        this.serviceManager = serviceManager;
        this.requestManager = requestManager;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof TextWebSocketFrame) {
            handleTextFrame(ctx, (TextWebSocketFrame) frame);
        } else if (frame instanceof BinaryWebSocketFrame) {
            handleBinaryFrame(ctx, (BinaryWebSocketFrame) frame);
        } else if (frame instanceof PingWebSocketFrame) {
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
        } else if (frame instanceof CloseWebSocketFrame) {
            ctx.close();
        }
    }

    private void handleTextFrame(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String text = frame.text();

        try {
            Message message = Message.fromJson(text);
            handleMessage(ctx, message);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse message", e);
            sendError(ctx, "Invalid message format");
        }
    }

    private void handleBinaryFrame(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) {
        ByteBuf content = frame.content();
        // Binary frames are used for file data streaming
        // First byte is message type, rest is data
        if (content.readableBytes() < 1) {
            return;
        }

        byte type = content.readByte();
        byte[] data = new byte[content.readableBytes()];
        content.readBytes(data);

        // Binary data is handled silently for performance
    }

    private void handleMessage(ChannelHandlerContext ctx, Message message) {
        String type = message.getType();
        
        switch (type) {
            case Message.TYPE_REGISTER:
                handleRegister(ctx, message);
                break;
            case Message.TYPE_UNREGISTER:
                handleUnregister(ctx, message);
                break;
            case Message.TYPE_CLIENT_REGISTER:
                handleClientRegister(ctx, message);
                break;
            case Message.TYPE_HTTP_RESPONSE:
                handleHttpResponse(ctx, message);
                break;
            case Message.TYPE_HTTP_RESPONSE_START:
                handleHttpResponseStart(ctx, message);
                break;
            case Message.TYPE_HTTP_RESPONSE_CHUNK:
                handleHttpResponseChunk(ctx, message);
                break;
            case Message.TYPE_FILE_REGISTER:
                handleFileRegister(ctx, message);
                break;
            case Message.TYPE_FILE_UNREGISTER:
                handleFileUnregister(ctx, message);
                break;
            case Message.TYPE_CHAT_JOIN:
                handleChatJoin(ctx, message);
                break;
            case Message.TYPE_CHAT_LEAVE:
                handleChatLeave(ctx, message);
                break;
            case Message.TYPE_CHAT_MESSAGE:
                handleChatMessage(ctx, message);
                break;
            case Message.TYPE_DATA:
                handleData(ctx, message);
                break;
            case Message.TYPE_KICK_CLIENT:
                handleKickClient(ctx, message);
                break;
            case Message.TYPE_FILE_LIST:
            case Message.TYPE_CHAT_USER_LIST:
                // These are broadcast messages from service to clients
                handleBroadcast(ctx, message);
                break;
            case Message.TYPE_FILE_REQUEST:
                handleFileRequest(ctx, message);
                break;
            case Message.TYPE_CREATE_INSTANCE:
                handleCreateInstance(ctx, message);
                break;
            case Message.TYPE_JOIN_INSTANCE:
                handleJoinInstance(ctx, message);
                break;
            case Message.TYPE_LIST_INSTANCES:
                handleListInstances(ctx, message);
                break;
            default:
                Log.w(TAG, "Unknown message type: " + type);
        }
    }

    private void handleRegister(ChannelHandlerContext ctx, Message message) {
        try {
            ServiceRegistration registration = Message.payloadAs(
                message.getPayload(), 
                ServiceRegistration.class
            );

            boolean success = serviceManager.registerService(registration, ctx.channel());
            
            // Send acknowledgment
            Message ack = new Message(Message.TYPE_ACK);
            ack.setId(message.getId());
            JsonObject payload = new JsonObject();
            payload.addProperty("success", success);
            payload.addProperty("message", success ? "Service registered" : "Registration failed");
            ack.setPayload(payload);
            
            ctx.writeAndFlush(new TextWebSocketFrame(ack.toJson()));
        } catch (Exception e) {
            Log.e(TAG, "Failed to register service", e);
            sendError(ctx, "Registration failed: " + e.getMessage());
        }
    }

    private void handleUnregister(ChannelHandlerContext ctx, Message message) {
        String serviceName = message.getService();
        if (serviceName != null) {
            serviceManager.unregisterService(serviceName);
        }
        
        Message ack = new Message(Message.TYPE_ACK);
        ack.setId(message.getId());
        ctx.writeAndFlush(new TextWebSocketFrame(ack.toJson()));
    }

    private void handleHttpResponse(ChannelHandlerContext ctx, Message message) {
        try {
            HttpRelayResponse response = Message.payloadAs(
                message.getPayload(),
                HttpRelayResponse.class
            );

            PendingRequest pending = requestManager.removePendingRequest(response.getRequestId());
            if (pending != null) {
                HttpHandler.sendRelayResponse(pending.getCtx(), response);
            } else {
                Log.w(TAG, "No pending request for: " + response.getRequestId());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to handle HTTP response", e);
        }
    }

    private void handleData(ChannelHandlerContext ctx, Message message) {
        // Handle service-specific data messages
        String service = message.getService();
        if (service != null) {
            ServiceManager.ServiceEntry entry = serviceManager.getService(service);
            if (entry != null) {
                // Broadcast or route data as needed
                Log.d(TAG, "Data message for service: " + service);
            }
        }
    }

    /**
     * Handle streaming HTTP response start (headers).
     */
    private void handleHttpResponseStart(ChannelHandlerContext ctx, Message message) {
        try {
            JsonObject payload = message.getPayload();
            String requestId = payload.get("request_id").getAsString();
            int status = payload.get("status").getAsInt();
            JsonObject headers = payload.getAsJsonObject("headers");

            PendingRequest pending = requestManager.getPendingRequest(requestId);
            if (pending != null) {
                HttpHandler.startStreamingResponse(pending.getCtx(), requestId, status, headers);
            } else {
                Log.w(TAG, "No pending request for streaming start: " + requestId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to handle HTTP response start", e);
        }
    }

    /**
     * Handle streaming HTTP response chunk.
     */
    private void handleHttpResponseChunk(ChannelHandlerContext ctx, Message message) {
        try {
            JsonObject payload = message.getPayload();
            String requestId = payload.get("request_id").getAsString();
            boolean done = payload.has("done") && payload.get("done").getAsBoolean();
            
            String chunkBase64 = payload.has("chunk_base64") ? 
                payload.get("chunk_base64").getAsString() : null;
            String error = payload.has("error") ? 
                payload.get("error").getAsString() : null;

            PendingRequest pending = done ? 
                requestManager.removePendingRequest(requestId) : 
                requestManager.getPendingRequest(requestId);
                
            if (pending != null) {
                if (error != null) {
                    HttpHandler.sendStreamingError(pending.getCtx(), error);
                } else if (chunkBase64 != null) {
                    HttpHandler.sendStreamingChunk(pending.getCtx(), chunkBase64);
                }
                
                if (done) {
                    HttpHandler.endStreamingResponse(pending.getCtx());
                }
            } else {
                Log.w(TAG, "No pending request for streaming chunk: " + requestId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to handle HTTP response chunk", e);
        }
    }

    /**
     * Handle file registration for relay sharing.
     */
    private void handleFileRegister(ChannelHandlerContext ctx, Message message) {
        try {
            JsonObject payload = message.getPayload();
            String fileId = payload.get("fileId").getAsString();
            String filename = payload.get("filename").getAsString();
            long size = payload.get("size").getAsLong();
            String mimeType = payload.has("mimeType") ? 
                payload.get("mimeType").getAsString() : "application/octet-stream";
            String ownerId = payload.has("ownerId") ? 
                payload.get("ownerId").getAsString() : "unknown";

            serviceManager.registerFile(fileId, filename, size, mimeType, ownerId, ctx.channel());
        } catch (Exception e) {
            Log.e(TAG, "Failed to register file", e);
        }
    }

    /**
     * Handle file unregistration.
     */
    private void handleFileUnregister(ChannelHandlerContext ctx, Message message) {
        try {
            JsonObject payload = message.getPayload();
            String fileId = payload.get("fileId").getAsString();
            serviceManager.unregisterFile(fileId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister file", e);
        }
    }
    
    /**
     * Handle client registration (for user clients, not services).
     * Clients can connect to share files or chat without taking over the service.
     */
    private void handleClientRegister(ChannelHandlerContext ctx, Message message) {
        try {
            JsonObject payload = message.getPayload();
            // Use service field first, then payload.clientType as fallback
            String clientType = message.getService();
            if (clientType == null || clientType.isEmpty()) {
                clientType = payload.has("clientType") ? payload.get("clientType").getAsString() : "unknown";
            }
            String userId = payload.has("userId") ? payload.get("userId").getAsString() : "u" + System.currentTimeMillis();
            
            // Validate service auth token if service requires it
            ServiceManager.ServiceEntry service = serviceManager.getService(clientType);
            if (service != null && service.hasAuth()) {
                String clientToken = payload.has("auth_token") ? payload.get("auth_token").getAsString() : null;
                if (!service.validateClientAuth(clientToken)) {
                    sendError(ctx, "Invalid service auth token");
                    return;
                }
            }
            
            // Register the client with the service manager
            serviceManager.registerClient(userId, clientType, ctx.channel());
            
            // Send acknowledgment
            Message ack = new Message(Message.TYPE_ACK);
            ack.setId(message.getId());
            JsonObject ackPayload = new JsonObject();
            ackPayload.addProperty("success", true);
            ackPayload.addProperty("message", "Client registered");
            ackPayload.addProperty("userId", userId);
            ack.setPayload(ackPayload);
            
            ctx.writeAndFlush(new TextWebSocketFrame(ack.toJson()));
            
            // Send current file list if fileshare client
            if ("fileshare".equals(clientType)) {
                serviceManager.broadcastFileList();
            }
            
            // Send current user list if chat client
            if ("chat".equals(clientType)) {
                serviceManager.broadcastChatUserList();
            }
            
            Log.i(TAG, "Client registered: " + userId + " (type: " + clientType + ")");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register client", e);
            sendError(ctx, "Client registration failed: " + e.getMessage());
        }
    }
    
    /**
     * Handle chat join message.
     */
    private void handleChatJoin(ChannelHandlerContext ctx, Message message) {
        try {
            JsonObject payload = message.getPayload();
            String userId = payload.get("userId").getAsString();
            String name = payload.has("name") ? payload.get("name").getAsString() : "Anonymous";
            
            // Register chat user
            serviceManager.registerChatUser(userId, name, ctx.channel());
            
            // Broadcast to all chat clients
            serviceManager.broadcastChatJoin(userId, name);
            serviceManager.broadcastChatUserList();
            
            Log.i(TAG, "Chat user joined: " + name + " (" + userId + ")");
        } catch (Exception e) {
            Log.e(TAG, "Failed to handle chat join", e);
        }
    }
    
    /**
     * Handle chat leave message.
     */
    private void handleChatLeave(ChannelHandlerContext ctx, Message message) {
        try {
            JsonObject payload = message.getPayload();
            String userId = payload.get("userId").getAsString();
            
            String name = serviceManager.getChatUserName(userId);
            serviceManager.unregisterChatUser(userId);
            
            // Broadcast to all chat clients
            if (name != null) {
                serviceManager.broadcastChatLeave(userId, name);
                serviceManager.broadcastChatUserList();
            }
            
            Log.i(TAG, "Chat user left: " + userId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to handle chat leave", e);
        }
    }
    
    /**
     * Handle chat message.
     */
    private void handleChatMessage(ChannelHandlerContext ctx, Message message) {
        try {
            JsonObject payload = message.getPayload();
            
            // Broadcast to all chat clients (or specific recipients if specified)
            serviceManager.broadcastChatMessage(payload, ctx.channel());
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to handle chat message", e);
        }
    }
    
    /**
     * Handle kick client request from service.
     */
    private void handleKickClient(ChannelHandlerContext ctx, Message message) {
        try {
            JsonObject payload = message.getPayload();
            String userId = payload.get("userId").getAsString();
            serviceManager.kickClient(userId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to kick client", e);
        }
    }
    
    /**
     * Handle create instance request from service client.
     */
    private void handleCreateInstance(ChannelHandlerContext ctx, Message message) {
        try {
            JsonObject payload = message.getPayload();
            if (payload == null) {
                payload = new JsonObject();
            }
            
            String serviceName = message.getService();
            if (serviceName == null || serviceName.isEmpty()) {
                sendError(ctx, "Service name is required");
                return;
            }
            
            String name = getStringFromPayload(payload, "name", "Room");
            String token = getStringFromPayload(payload, "token", null);
            
            // Validate server auth
            ServerConfig config = HttpHandler.getServerConfig();
            if (config != null && config.isAuthEnabled()) {
                String authToken = getStringFromPayload(payload, "server_token", null);
                if (!config.validateServerAuth(authToken)) {
                    sendError(ctx, "Invalid server auth token");
                    return;
                }
            }
            
            ServiceManager.ServiceInstance instance = serviceManager.createInstance(
                serviceName, name, token, ctx.channel()
            );
            
            // Send success response
            Message ack = new Message(Message.TYPE_INSTANCE_CREATED);
            ack.setService(serviceName);
            JsonObject ackPayload = new JsonObject();
            ackPayload.addProperty("success", true);
            ackPayload.addProperty("uuid", instance.getUuid());
            ackPayload.addProperty("name", instance.getName());
            ackPayload.addProperty("hasToken", instance.hasToken());
            ack.setPayload(ackPayload);
            ctx.writeAndFlush(new TextWebSocketFrame(ack.toJson()));
            
            Log.i(TAG, "Instance created: " + instance.getUuid() + " (" + name + ")");
        } catch (Exception e) {
            Log.e(TAG, "Failed to create instance", e);
            sendError(ctx, "Failed to create instance: " + e.getMessage());
        }
    }
    
    /**
     * Handle join instance request from user client.
     */
    private void handleJoinInstance(ChannelHandlerContext ctx, Message message) {
        try {
            JsonObject payload = message.getPayload();
            if (payload == null) {
                sendError(ctx, "Missing payload");
                return;
            }
            
            String serviceName = message.getService();
            String instanceUuid = getStringFromPayload(payload, "uuid", null);
            if (instanceUuid == null || instanceUuid.isEmpty()) {
                sendError(ctx, "Instance UUID is required");
                return;
            }
            String userId = getStringFromPayload(payload, "userId", "u" + System.currentTimeMillis());
            String token = getStringFromPayload(payload, "token", null);
            
            // Find the instance
            ServiceManager.ServiceInstance instance = serviceManager.getInstance(instanceUuid);
            if (instance == null || !instance.isOwnerConnected()) {
                sendError(ctx, "Instance not found or not available");
                return;
            }
            
            // Validate instance token
            if (!instance.validateToken(token)) {
                sendError(ctx, "Invalid instance token");
                return;
            }
            
            // Register client to instance
            serviceManager.registerClient(userId, serviceName, instanceUuid, ctx.channel());
            
            // Send success response
            Message ack = new Message(Message.TYPE_ACK);
            ack.setService(serviceName);
            JsonObject ackPayload = new JsonObject();
            ackPayload.addProperty("success", true);
            ackPayload.addProperty("message", "Joined instance");
            ackPayload.addProperty("userId", userId);
            ackPayload.addProperty("instanceUuid", instanceUuid);
            ackPayload.addProperty("instanceName", instance.getName());
            ack.setPayload(ackPayload);
            ctx.writeAndFlush(new TextWebSocketFrame(ack.toJson()));
            
            Log.i(TAG, "Client " + userId + " joined instance " + instanceUuid);
        } catch (Exception e) {
            Log.e(TAG, "Failed to join instance", e);
            sendError(ctx, "Failed to join instance: " + e.getMessage());
        }
    }
    
    /**
     * Handle list instances request.
     */
    private void handleListInstances(ChannelHandlerContext ctx, Message message) {
        try {
            String serviceName = message.getService();
            
            List<ServiceManager.ServiceInstance> instances = serviceManager.getInstancesForService(serviceName);
            
            Message response = new Message(Message.TYPE_INSTANCE_LIST);
            response.setService(serviceName);
            JsonObject payload = new JsonObject();
            JsonArray arr = new JsonArray();
            for (ServiceManager.ServiceInstance inst : instances) {
                arr.add(inst.toJson());
            }
            payload.add("instances", arr);
            response.setPayload(payload);
            
            ctx.writeAndFlush(new TextWebSocketFrame(response.toJson()));
        } catch (Exception e) {
            Log.e(TAG, "Failed to list instances", e);
            sendError(ctx, "Failed to list instances: " + e.getMessage());
        }
    }
    
    /**
     * Handle broadcast messages from service to all clients.
     */
    private void handleBroadcast(ChannelHandlerContext ctx, Message message) {
        try {
            String service = message.getService();
            if (service == null) return;
            
            String json = message.toJson();
            
            // Send to all clients of this service type
            for (ServiceManager.ClientInfo client : serviceManager.getClientsByType(service)) {
                if (client.getChannel() != null && client.getChannel().isActive() && client.getChannel() != ctx.channel()) {
                    client.getChannel().writeAndFlush(new TextWebSocketFrame(json));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to broadcast message", e);
        }
    }
    
    /**
     * Handle file request from service to specific client.
     */
    private void handleFileRequest(ChannelHandlerContext ctx, Message message) {
        try {
            JsonObject payload = message.getPayload();
            String requestId = payload.get("requestId").getAsString();
            String fileId = payload.get("fileId").getAsString();
            String ownerId = payload.has("ownerId") ? payload.get("ownerId").getAsString() : null;
            
            // Find the file owner and send the request
            if (ownerId != null) {
                ServiceManager.ClientInfo owner = serviceManager.getClient(ownerId);
                if (owner != null && owner.getChannel() != null && owner.getChannel().isActive()) {
                    // Forward the request to the owner
                    Message request = new Message(Message.TYPE_FILE_REQUEST);
                    request.setService(message.getService());
                    JsonObject reqPayload = new JsonObject();
                    reqPayload.addProperty("requestId", requestId);
                    reqPayload.addProperty("fileId", fileId);
                    request.setPayload(reqPayload);
                    owner.getChannel().writeAndFlush(new TextWebSocketFrame(request.toJson()));
                    return;
                }
            }
            
            // Owner not found
            Log.w(TAG, "File owner not found: " + ownerId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to handle file request", e);
        }
    }

    /**
     * Safely get a string from JsonObject, handling null and JsonNull.
     */
    private String getStringFromPayload(JsonObject payload, String key, String defaultValue) {
        if (payload == null || !payload.has(key)) {
            return defaultValue;
        }
        try {
            if (payload.get(key).isJsonNull()) {
                return defaultValue;
            }
            return payload.get(key).getAsString();
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private void sendError(ChannelHandlerContext ctx, String error) {
        Message errorMsg = new Message(Message.TYPE_ERROR);
        JsonObject payload = new JsonObject();
        payload.addProperty("message", error);  // Client expects 'message' field
        payload.addProperty("error", error);    // Also include 'error' for compatibility
        errorMsg.setPayload(payload);
        ctx.writeAndFlush(new TextWebSocketFrame(errorMsg.toJson()));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Log.i(TAG, "WebSocket channel disconnected");
        
        // Clean up any pending requests for this service
        cleanupPendingRequestsForChannel(ctx);
        
        serviceManager.onChannelDisconnect(ctx.channel());
        super.channelInactive(ctx);
    }

    /**
     * Clean up pending HTTP requests when a service client disconnects.
     * This sends error responses to HTTP clients waiting for relay responses.
     */
    private void cleanupPendingRequestsForChannel(ChannelHandlerContext ctx) {
        // Find and fail all pending requests that were being handled by this channel
        // The RequestManager should have a method to get requests by service
        // For now, we rely on the timeout mechanism, but we can improve this
        Log.d(TAG, "Cleaning up pending requests for disconnected channel");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Log.e(TAG, "WebSocket error", cause);
        ctx.close();
    }
}
