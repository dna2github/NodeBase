package seven.lab.wstun.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

/**
 * Base message class for WebSocket communication.
 * Supports both text (JSON) and binary modes.
 */
public class Message {
    
    private static final Gson gson = new Gson();

    // Message types
    public static final String TYPE_REGISTER = "register";
    public static final String TYPE_UNREGISTER = "unregister";
    public static final String TYPE_DATA = "data";
    public static final String TYPE_FILE_REQUEST = "file_request";
    public static final String TYPE_FILE_DATA = "file_data";
    public static final String TYPE_FILE_END = "file_end";
    public static final String TYPE_CHAT_MESSAGE = "chat_message";
    public static final String TYPE_CHAT_JOIN = "chat_join";
    public static final String TYPE_CHAT_LEAVE = "chat_leave";
    public static final String TYPE_ERROR = "error";
    public static final String TYPE_ACK = "ack";
    public static final String TYPE_RELAY_REGISTER = "relay_register";
    public static final String TYPE_RELAY_REQUEST = "relay_request";
    public static final String TYPE_HTTP_REQUEST = "http_request";
    public static final String TYPE_HTTP_RESPONSE = "http_response";
    
    // Streaming response types for large file transfers
    public static final String TYPE_HTTP_RESPONSE_START = "http_response_start";
    public static final String TYPE_HTTP_RESPONSE_CHUNK = "http_response_chunk";
    
    // File registry types for relay file sharing
    public static final String TYPE_FILE_REGISTER = "file_register";
    public static final String TYPE_FILE_UNREGISTER = "file_unregister";
    public static final String TYPE_FILE_LIST = "file_list";
    
    // Client registration (for user clients, not services)
    public static final String TYPE_CLIENT_REGISTER = "client_register";
    
    // Chat broadcast types
    public static final String TYPE_CHAT_USER_LIST = "chat_user_list";
    
    // Service management
    public static final String TYPE_KICK_CLIENT = "kick_client";
    
    // Instance management
    public static final String TYPE_CREATE_INSTANCE = "create_instance";
    public static final String TYPE_JOIN_INSTANCE = "join_instance";
    public static final String TYPE_LEAVE_INSTANCE = "leave_instance";
    public static final String TYPE_LIST_INSTANCES = "list_instances";
    public static final String TYPE_INSTANCE_LIST = "instance_list";
    public static final String TYPE_INSTANCE_CREATED = "instance_created";
    public static final String TYPE_RECLAIM_INSTANCE = "reclaim_instance";
    public static final String TYPE_INSTANCE_RECLAIMED = "instance_reclaimed";
    
    // Instance-scoped messages (for fileshare/chat within a room)
    public static final String TYPE_USER_JOIN = "user_join";
    public static final String TYPE_USER_LEAVE = "user_leave";
    public static final String TYPE_FILE_ADD = "file_add";
    public static final String TYPE_FILE_REMOVE = "file_remove";
    public static final String TYPE_REQUEST_STATE = "request_state";
    public static final String TYPE_STATE_RESPONSE = "state_response";

    @SerializedName("type")
    private String type;

    @SerializedName("id")
    private String id;

    @SerializedName("service")
    private String service;

    @SerializedName("payload")
    private JsonObject payload;

    @SerializedName("binary")
    private boolean binary;

    public Message() {
    }

    public Message(String type) {
        this.type = type;
        this.id = generateId();
    }

    public Message(String type, String service) {
        this(type);
        this.service = service;
    }

    private String generateId() {
        return String.valueOf(System.currentTimeMillis()) + "-" + (int)(Math.random() * 10000);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public JsonObject getPayload() {
        return payload;
    }

    public void setPayload(JsonObject payload) {
        this.payload = payload;
    }

    public boolean isBinary() {
        return binary;
    }

    public void setBinary(boolean binary) {
        this.binary = binary;
    }

    public String toJson() {
        return gson.toJson(this);
    }

    public static Message fromJson(String json) {
        return gson.fromJson(json, Message.class);
    }

    public static <T> T payloadAs(JsonObject payload, Class<T> clazz) {
        return gson.fromJson(payload, clazz);
    }

    public static JsonObject toPayload(Object obj) {
        return gson.toJsonTree(obj).getAsJsonObject();
    }
}
