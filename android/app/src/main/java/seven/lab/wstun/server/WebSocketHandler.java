package seven.lab.wstun.server;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

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
        Log.d(TAG, "Received text: " + text);

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

        // Handle binary data based on type
        Log.d(TAG, "Received binary frame, type: " + type + ", length: " + data.length);
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
            case Message.TYPE_DATA:
                handleData(ctx, message);
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

    private void sendError(ChannelHandlerContext ctx, String error) {
        Message errorMsg = new Message(Message.TYPE_ERROR);
        JsonObject payload = new JsonObject();
        payload.addProperty("error", error);
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
