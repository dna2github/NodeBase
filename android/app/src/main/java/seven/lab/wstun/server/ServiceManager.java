package seven.lab.wstun.server;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import seven.lab.wstun.protocol.Message;
import seven.lab.wstun.protocol.ServiceRegistration;

/**
 * Manages registered services, instances, and their WebSocket connections.
 * 
 * Architecture:
 * - Service: A type of service (e.g., fileshare, chat)
 * - ServiceInstance: A specific room/session with UUID, name, and optional token
 * - User: Connected to a specific instance
 */
public class ServiceManager {
    
    private static final String TAG = "ServiceManager";

    // Service name -> ServiceEntry (the service provider)
    private final Map<String, ServiceEntry> services = new ConcurrentHashMap<>();
    
    // Instance UUID -> ServiceInstance
    private final Map<String, ServiceInstance> instances = new ConcurrentHashMap<>();
    
    // Service name -> List of instance UUIDs
    private final Map<String, List<String>> serviceInstances = new ConcurrentHashMap<>();
    
    // Channel -> Service name (for cleanup on disconnect)
    private final Map<Channel, String> channelToService = new ConcurrentHashMap<>();
    
    // Channel -> Instance UUID (for instance owner cleanup)
    private final Map<Channel, String> channelToInstance = new ConcurrentHashMap<>();

    // File registry: fileId -> FileInfo (for relay file sharing)
    private final Map<String, FileInfo> fileRegistry = new ConcurrentHashMap<>();
    
    // Channel -> List of file IDs owned by that channel
    private final Map<Channel, List<String>> channelToFiles = new ConcurrentHashMap<>();
    
    // Client registry: userId -> ClientInfo (for user clients)
    private final Map<String, ClientInfo> clients = new ConcurrentHashMap<>();
    
    // Channel -> userId (for client cleanup on disconnect)
    private final Map<Channel, String> channelToClient = new ConcurrentHashMap<>();
    
    // Chat users: userId -> ChatUser
    private final Map<String, ChatUser> chatUsers = new ConcurrentHashMap<>();

    private ServiceChangeListener listener;
    private RequestManager requestManager;
    
    /**
     * Represents a service instance (room/session).
     */
    public static class ServiceInstance {
        private final String uuid;
        private final String serviceName;
        private final String name;
        private final String token;
        private final Channel ownerChannel;
        private final long createdAt;
        private final List<String> userIds = new ArrayList<>();
        
        public ServiceInstance(String uuid, String serviceName, String name, String token, Channel ownerChannel) {
            this.uuid = uuid;
            this.serviceName = serviceName;
            this.name = name;
            this.token = token;
            this.ownerChannel = ownerChannel;
            this.createdAt = System.currentTimeMillis();
        }
        
        public String getUuid() { return uuid; }
        public String getServiceName() { return serviceName; }
        public String getName() { return name; }
        public String getToken() { return token; }
        public Channel getOwnerChannel() { return ownerChannel; }
        public long getCreatedAt() { return createdAt; }
        public List<String> getUserIds() { return userIds; }
        
        public boolean hasToken() {
            return token != null && !token.isEmpty();
        }
        
        public boolean validateToken(String inputToken) {
            if (!hasToken()) return true;
            return token.equals(inputToken);
        }
        
        public void addUser(String userId) {
            if (!userIds.contains(userId)) {
                userIds.add(userId);
            }
        }
        
        public void removeUser(String userId) {
            userIds.remove(userId);
        }
        
        public boolean isOwnerConnected() {
            return ownerChannel != null && ownerChannel.isActive();
        }
        
        public int getUserCount() {
            return userIds.size();
        }
        
        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("uuid", uuid);
            obj.addProperty("service", serviceName);
            obj.addProperty("name", name);
            obj.addProperty("hasToken", hasToken());
            obj.addProperty("createdAt", createdAt);
            obj.addProperty("userCount", userIds.size());
            return obj;
        }
    }

    /**
     * Represents a connected client (user, not service).
     */
    public static class ClientInfo {
        private final String userId;
        private final String clientType;
        private final String instanceUuid;
        private final Channel channel;
        private final long connectedAt;

        public ClientInfo(String userId, String clientType, String instanceUuid, Channel channel) {
            this.userId = userId;
            this.clientType = clientType;
            this.instanceUuid = instanceUuid;
            this.channel = channel;
            this.connectedAt = System.currentTimeMillis();
        }

        public String getUserId() { return userId; }
        public String getClientType() { return clientType; }
        public String getInstanceUuid() { return instanceUuid; }
        public Channel getChannel() { return channel; }
        public long getConnectedAt() { return connectedAt; }
    }
    
    /**
     * Represents a chat user.
     */
    public static class ChatUser {
        private final String userId;
        private String name;
        private final Channel channel;
        private final long joinedAt;

        public ChatUser(String userId, String name, Channel channel) {
            this.userId = userId;
            this.name = name;
            this.channel = channel;
            this.joinedAt = System.currentTimeMillis();
        }

        public String getUserId() { return userId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Channel getChannel() { return channel; }
        public long getJoinedAt() { return joinedAt; }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", userId);
            obj.addProperty("name", name);
            obj.addProperty("joinedAt", joinedAt);
            return obj;
        }
    }

    /**
     * Represents a file registered for relay sharing.
     */
    public static class FileInfo {
        private final String fileId;
        private final String filename;
        private final long size;
        private final String mimeType;
        private final String ownerId;
        private final Channel ownerChannel;

        public FileInfo(String fileId, String filename, long size, String mimeType, 
                       String ownerId, Channel ownerChannel) {
            this.fileId = fileId;
            this.filename = filename;
            this.size = size;
            this.mimeType = mimeType;
            this.ownerId = ownerId;
            this.ownerChannel = ownerChannel;
        }

        public String getFileId() { return fileId; }
        public String getFilename() { return filename; }
        public long getSize() { return size; }
        public String getMimeType() { return mimeType; }
        public String getOwnerId() { return ownerId; }
        public Channel getOwnerChannel() { return ownerChannel; }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", fileId);
            obj.addProperty("filename", filename);
            obj.addProperty("size", size);
            obj.addProperty("mimeType", mimeType);
            obj.addProperty("ownerId", ownerId);
            return obj;
        }
    }

    public interface ServiceChangeListener {
        void onServiceAdded(ServiceEntry service);
        void onServiceRemoved(ServiceEntry service);
    }

    /**
     * Represents a registered service.
     */
    public static class ServiceEntry {
        private final String name;
        private final String type;
        private final String description;
        private final Channel channel;
        private final ServiceRegistration registration;
        private final long registeredAt;
        private final String authToken;

        public ServiceEntry(ServiceRegistration registration, Channel channel) {
            this.name = registration.getName();
            this.type = registration.getType();
            this.description = registration.getDescription();
            this.channel = channel;
            this.registration = registration;
            this.registeredAt = System.currentTimeMillis();
            this.authToken = registration.getAuthToken();
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }

        public Channel getChannel() {
            return channel;
        }

        public ServiceRegistration getRegistration() {
            return registration;
        }

        public long getRegisteredAt() {
            return registeredAt;
        }

        public boolean isConnected() {
            return channel != null && channel.isActive();
        }
        
        public String getAuthToken() {
            return authToken;
        }
        
        public boolean hasAuth() {
            return authToken != null && !authToken.isEmpty();
        }
        
        /**
         * Validate client auth token for this service.
         * Returns true if no auth required or token matches.
         */
        public boolean validateClientAuth(String token) {
            if (!hasAuth()) {
                return true;
            }
            return authToken.equals(token);
        }
    }

    public void setListener(ServiceChangeListener listener) {
        this.listener = listener;
    }

    public void setRequestManager(RequestManager requestManager) {
        this.requestManager = requestManager;
    }

    // Reserved service names that cannot be used
    private static final java.util.Set<String> RESERVED_NAMES = new java.util.HashSet<>(
        java.util.Arrays.asList(
            "debug", "admin", "_api", "ws", "websocket", 
            "api", "system", "config", "static", "assets",
            "libwstun.js"
        )
    );
    
    /**
     * Check if a service name is reserved.
     */
    public static boolean isReservedName(String name) {
        if (name == null) return true;
        return RESERVED_NAMES.contains(name.toLowerCase());
    }
    
    /**
     * Register a new service.
     */
    public boolean registerService(ServiceRegistration registration, Channel channel) {
        String name = registration.getName();
        
        if (name == null || name.isEmpty()) {
            Log.w(TAG, "Service registration failed: no name provided");
            return false;
        }
        
        // Check for reserved names
        if (isReservedName(name)) {
            Log.w(TAG, "Service registration failed: reserved name: " + name);
            return false;
        }

        // Check if service with same name exists
        if (services.containsKey(name)) {
            Log.w(TAG, "Service already exists: " + name);
            return false;
        }

        ServiceEntry entry = new ServiceEntry(registration, channel);
        services.put(name, entry);
        channelToService.put(channel, name);

        Log.i(TAG, "Service registered: " + name + " (type: " + registration.getType() + ")");

        if (listener != null) {
            listener.onServiceAdded(entry);
        }

        return true;
    }

    /**
     * Unregister a service by name.
     */
    public void unregisterService(String name) {
        ServiceEntry entry = services.remove(name);
        if (entry != null) {
            channelToService.remove(entry.getChannel());
            Log.i(TAG, "Service unregistered: " + name);

            if (listener != null) {
                listener.onServiceRemoved(entry);
            }
        }
    }

    /**
     * Create a new service instance (room/session).
     */
    public ServiceInstance createInstance(String serviceName, String name, String token, Channel ownerChannel) {
        String uuid = java.util.UUID.randomUUID().toString().substring(0, 8);
        
        ServiceInstance instance = new ServiceInstance(uuid, serviceName, name, token, ownerChannel);
        instances.put(uuid, instance);
        channelToInstance.put(ownerChannel, uuid);
        
        // Track instances per service
        serviceInstances.computeIfAbsent(serviceName, k -> new ArrayList<>()).add(uuid);
        
        Log.i(TAG, "Instance created: " + uuid + " (" + name + ") for service " + serviceName);
        return instance;
    }
    
    /**
     * Get an instance by UUID.
     */
    public ServiceInstance getInstance(String uuid) {
        return instances.get(uuid);
    }
    
    /**
     * Get all instances for a service.
     */
    public List<ServiceInstance> getInstancesForService(String serviceName) {
        List<String> uuids = serviceInstances.get(serviceName);
        if (uuids == null) return new ArrayList<>();
        
        List<ServiceInstance> result = new ArrayList<>();
        for (String uuid : uuids) {
            ServiceInstance inst = instances.get(uuid);
            if (inst != null && inst.isOwnerConnected()) {
                result.add(inst);
            }
        }
        return result;
    }
    
    /**
     * Remove an instance.
     */
    public void removeInstance(String uuid) {
        ServiceInstance instance = instances.remove(uuid);
        if (instance != null) {
            channelToInstance.remove(instance.getOwnerChannel());
            List<String> uuids = serviceInstances.get(instance.getServiceName());
            if (uuids != null) {
                uuids.remove(uuid);
            }
            Log.i(TAG, "Instance removed: " + uuid);
        }
    }
    
    /**
     * Destroy an instance (kick all users and close owner connection).
     */
    public void destroyInstance(String uuid) {
        ServiceInstance instance = instances.get(uuid);
        if (instance == null) {
            return;
        }
        
        // Kick all users in the instance
        for (String userId : new ArrayList<>(instance.getUserIds())) {
            kickClient(userId);
        }
        
        // Close owner connection
        Channel ownerChannel = instance.getOwnerChannel();
        if (ownerChannel != null && ownerChannel.isActive()) {
            Message msg = new Message("instance_destroyed");
            JsonObject payload = new JsonObject();
            payload.addProperty("uuid", uuid);
            payload.addProperty("reason", "Instance destroyed by administrator");
            msg.setPayload(payload);
            ownerChannel.writeAndFlush(new TextWebSocketFrame(msg.toJson()));
            ownerChannel.close();
        }
        
        // Remove instance
        removeInstance(uuid);
        Log.i(TAG, "Instance destroyed: " + uuid);
    }
    
    /**
     * Close all instances for a service (when disabling/uninstalling).
     */
    public void closeInstancesForService(String serviceName) {
        List<String> uuids = serviceInstances.get(serviceName);
        if (uuids == null || uuids.isEmpty()) {
            return;
        }
        
        // Make a copy to avoid concurrent modification
        List<String> toRemove = new ArrayList<>(uuids);
        
        for (String uuid : toRemove) {
            ServiceInstance instance = instances.get(uuid);
            if (instance != null) {
                // Kick all users in the instance
                for (String userId : new ArrayList<>(instance.getUserIds())) {
                    kickClient(userId);
                }
                
                // Close owner connection
                Channel ownerChannel = instance.getOwnerChannel();
                if (ownerChannel != null && ownerChannel.isActive()) {
                    Message msg = new Message("service_disabled");
                    JsonObject payload = new JsonObject();
                    payload.addProperty("serviceName", serviceName);
                    payload.addProperty("reason", "Service disabled");
                    msg.setPayload(payload);
                    ownerChannel.writeAndFlush(new TextWebSocketFrame(msg.toJson()));
                    ownerChannel.close();
                }
                
                // Remove instance
                removeInstance(uuid);
            }
        }
        
        Log.i(TAG, "Closed all instances for service: " + serviceName);
    }
    
    /**
     * Get count of running instances for a service.
     */
    public int getInstanceCountForService(String serviceName) {
        return getInstancesForService(serviceName).size();
    }
    
    /**
     * Clean up instance for a disconnected channel.
     */
    private void cleanupInstanceForChannel(Channel channel) {
        String uuid = channelToInstance.remove(channel);
        if (uuid != null) {
            ServiceInstance instance = instances.remove(uuid);
            if (instance != null) {
                List<String> uuids = serviceInstances.get(instance.getServiceName());
                if (uuids != null) {
                    uuids.remove(uuid);
                }
                Log.i(TAG, "Instance cleaned up for disconnected channel: " + uuid);
            }
        }
    }

    /**
     * Handle channel disconnect - unregister associated service and clean up files.
     */
    public void onChannelDisconnect(Channel channel) {
        // Clean up files owned by this channel
        cleanupFilesForChannel(channel);
        
        // Clean up client if any
        cleanupClientForChannel(channel);
        
        // Clean up instance if any
        cleanupInstanceForChannel(channel);
        
        String serviceName = channelToService.remove(channel);
        if (serviceName != null) {
            ServiceEntry entry = services.remove(serviceName);
            if (entry != null) {
                Log.i(TAG, "Service disconnected: " + serviceName);
                
                // Fail any pending HTTP requests for this service
                if (requestManager != null) {
                    requestManager.failRequestsForService(serviceName);
                }
                
                if (listener != null) {
                    listener.onServiceRemoved(entry);
                }
            }
        }
    }

    /**
     * Get a service by name.
     */
    public ServiceEntry getService(String name) {
        return services.get(name);
    }

    /**
     * Get all registered services.
     */
    public List<ServiceEntry> getAllServices() {
        return new ArrayList<>(services.values());
    }

    /**
     * Check if a service exists.
     */
    public boolean hasService(String name) {
        return services.containsKey(name);
    }

    /**
     * Get the number of registered services.
     */
    public int getServiceCount() {
        return services.size();
    }

    /**
     * Kick (disconnect) a service.
     */
    public void kickService(String name) {
        ServiceEntry entry = services.get(name);
        if (entry != null && entry.getChannel() != null) {
            entry.getChannel().close();
            // The channel close will trigger onChannelDisconnect
        }
    }

    /**
     * Clear all services.
     */
    public void clear() {
        for (ServiceEntry entry : services.values()) {
            if (entry.getChannel() != null) {
                entry.getChannel().close();
            }
        }
        services.clear();
        channelToService.clear();
        fileRegistry.clear();
        channelToFiles.clear();
    }

    // ==================== File Registry Methods ====================

    /**
     * Register a file for relay sharing.
     */
    public void registerFile(String fileId, String filename, long size, String mimeType, 
                            String ownerId, Channel ownerChannel) {
        FileInfo info = new FileInfo(fileId, filename, size, mimeType, ownerId, ownerChannel);
        fileRegistry.put(fileId, info);
        
        // Track files by channel for cleanup
        channelToFiles.computeIfAbsent(ownerChannel, k -> new ArrayList<>()).add(fileId);
        
        Log.i(TAG, "File registered: " + filename + " (" + fileId + ") by " + ownerId);
        
        // Broadcast updated file list to all fileshare service clients
        broadcastFileList();
    }

    /**
     * Unregister a file from relay sharing.
     */
    public void unregisterFile(String fileId) {
        FileInfo info = fileRegistry.remove(fileId);
        if (info != null) {
            List<String> files = channelToFiles.get(info.getOwnerChannel());
            if (files != null) {
                files.remove(fileId);
            }
            Log.i(TAG, "File unregistered: " + info.getFilename() + " (" + fileId + ")");
            broadcastFileList();
        }
    }

    /**
     * Get file info by ID.
     */
    public FileInfo getFile(String fileId) {
        return fileRegistry.get(fileId);
    }

    /**
     * Get all registered files.
     */
    public List<FileInfo> getAllFiles() {
        return new ArrayList<>(fileRegistry.values());
    }

    /**
     * Handle channel disconnect - clean up files owned by this channel.
     */
    private void cleanupFilesForChannel(Channel channel) {
        List<String> files = channelToFiles.remove(channel);
        if (files != null && !files.isEmpty()) {
            for (String fileId : files) {
                fileRegistry.remove(fileId);
            }
            Log.i(TAG, "Cleaned up " + files.size() + " files for disconnected channel");
            broadcastFileList();
        }
    }

    /**
     * Broadcast updated file list to all connected fileshare clients.
     */
    public void broadcastFileList() {
        // Build file list JSON
        JsonArray filesArray = new JsonArray();
        for (FileInfo file : fileRegistry.values()) {
            filesArray.add(file.toJson());
        }

        Message message = new Message(Message.TYPE_FILE_LIST);
        JsonObject payload = new JsonObject();
        payload.add("files", filesArray);
        message.setPayload(payload);

        String json = message.toJson();

        // Send to all fileshare clients
        for (ClientInfo client : clients.values()) {
            if ("fileshare".equals(client.getClientType()) && client.getChannel().isActive()) {
                client.getChannel().writeAndFlush(new TextWebSocketFrame(json));
            }
        }

        // Also send to any other channels that have registered files
        for (Channel channel : channelToFiles.keySet()) {
            if (channel.isActive()) {
                channel.writeAndFlush(new TextWebSocketFrame(json));
            }
        }
    }
    
    // ==================== Client Registry Methods ====================
    
    /**
     * Register a user client (not a service).
     */
    public void registerClient(String userId, String clientType, Channel channel) {
        registerClient(userId, clientType, null, channel);
    }
    
    /**
     * Register a user client to a specific instance.
     */
    public void registerClient(String userId, String clientType, String instanceUuid, Channel channel) {
        ClientInfo info = new ClientInfo(userId, clientType, instanceUuid, channel);
        clients.put(userId, info);
        channelToClient.put(channel, userId);
        
        // Add user to instance if specified
        if (instanceUuid != null) {
            ServiceInstance instance = instances.get(instanceUuid);
            if (instance != null) {
                instance.addUser(userId);
            }
        }
        
        Log.i(TAG, "Client registered: " + userId + " (type: " + clientType + 
            (instanceUuid != null ? ", instance: " + instanceUuid : "") + ")");
        
        // Notify the instance owner that a client connected
        notifyInstanceOwner(instanceUuid, clientType, "client_connected", userId);
    }
    
    /**
     * Unregister a client.
     */
    public void unregisterClient(String userId) {
        ClientInfo info = clients.remove(userId);
        if (info != null) {
            channelToClient.remove(info.getChannel());
            Log.i(TAG, "Client unregistered: " + userId);
        }
    }
    
    /**
     * Get a client by userId.
     */
    public ClientInfo getClient(String userId) {
        return clients.get(userId);
    }
    
    /**
     * Get all clients of a specific type.
     */
    public List<ClientInfo> getClientsByType(String clientType) {
        List<ClientInfo> result = new ArrayList<>();
        for (ClientInfo client : clients.values()) {
            if (clientType.equals(client.getClientType())) {
                result.add(client);
            }
        }
        return result;
    }
    
    /**
     * Kick a client by userId.
     * @return true if client was found and kicked
     */
    public boolean kickClient(String userId) {
        ClientInfo client = clients.get(userId);
        if (client == null) {
            return false;
        }
        
        // Send kick message to client
        Message kickMsg = new Message("kick");
        JsonObject payload = new JsonObject();
        payload.addProperty("reason", "Kicked by administrator");
        kickMsg.setPayload(payload);
        
        if (client.getChannel() != null && client.getChannel().isActive()) {
            client.getChannel().writeAndFlush(new TextWebSocketFrame(kickMsg.toJson()));
            client.getChannel().close();
        }
        
        // Clean up
        clients.remove(userId);
        channelToClient.remove(client.getChannel());
        
        // Also remove from chat users if applicable
        ChatUser chatUser = chatUsers.remove(userId);
        if (chatUser != null) {
            broadcastChatLeave(userId, chatUser.getName());
            broadcastChatUserList();
        }
        
        Log.i(TAG, "Kicked client: " + userId);
        return true;
    }
    
    /**
     * Clean up clients for a disconnected channel.
     */
    private void cleanupClientForChannel(Channel channel) {
        String userId = channelToClient.remove(channel);
        if (userId != null) {
            ClientInfo client = clients.remove(userId);
            Log.i(TAG, "Cleaned up client for disconnected channel: " + userId);
            
            // Remove from instance
            if (client != null && client.getInstanceUuid() != null) {
                ServiceInstance instance = instances.get(client.getInstanceUuid());
                if (instance != null) {
                    instance.removeUser(userId);
                }
                notifyInstanceOwner(client.getInstanceUuid(), client.getClientType(), "client_disconnected", userId);
            }
            
            // Also clean up chat user if any
            ChatUser chatUser = chatUsers.remove(userId);
            if (chatUser != null) {
                broadcastChatLeave(userId, chatUser.getName());
                broadcastChatUserList();
            }
        }
    }
    
    /**
     * Notify an instance owner when a client connects or disconnects.
     */
    private void notifyInstanceOwner(String instanceUuid, String serviceName, String eventType, String userId) {
        ServiceInstance instance = instanceUuid != null ? instances.get(instanceUuid) : null;
        Channel ownerChannel = instance != null ? instance.getOwnerChannel() : null;
        
        // If no instance, fall back to service channel
        if (ownerChannel == null) {
            ServiceEntry service = services.get(serviceName);
            if (service != null && service.isConnected()) {
                ownerChannel = service.getChannel();
            }
        }
        
        if (ownerChannel != null && ownerChannel.isActive()) {
            Message message = new Message(eventType);
            message.setService(serviceName);
            JsonObject payload = new JsonObject();
            payload.addProperty("userId", userId);
            if (instanceUuid != null) {
                payload.addProperty("instanceUuid", instanceUuid);
            }
            message.setPayload(payload);
            ownerChannel.writeAndFlush(new TextWebSocketFrame(message.toJson()));
        }
    }
    
    // ==================== Chat User Methods ====================
    
    /**
     * Register a chat user.
     */
    public void registerChatUser(String userId, String name, Channel channel) {
        ChatUser user = new ChatUser(userId, name, channel);
        chatUsers.put(userId, user);
        Log.i(TAG, "Chat user registered: " + name + " (" + userId + ")");
    }
    
    /**
     * Unregister a chat user.
     */
    public void unregisterChatUser(String userId) {
        chatUsers.remove(userId);
        Log.i(TAG, "Chat user unregistered: " + userId);
    }
    
    /**
     * Get chat user name.
     */
    public String getChatUserName(String userId) {
        ChatUser user = chatUsers.get(userId);
        return user != null ? user.getName() : null;
    }
    
    /**
     * Broadcast chat user list to all chat clients.
     */
    public void broadcastChatUserList() {
        JsonArray usersArray = new JsonArray();
        for (ChatUser user : chatUsers.values()) {
            usersArray.add(user.toJson());
        }

        Message message = new Message(Message.TYPE_CHAT_USER_LIST);
        JsonObject payload = new JsonObject();
        payload.add("users", usersArray);
        message.setPayload(payload);

        String json = message.toJson();

        // Send to all chat clients
        for (ClientInfo client : clients.values()) {
            if ("chat".equals(client.getClientType()) && client.getChannel().isActive()) {
                client.getChannel().writeAndFlush(new TextWebSocketFrame(json));
            }
        }
    }
    
    /**
     * Broadcast chat join notification.
     */
    public void broadcastChatJoin(String userId, String name) {
        Message message = new Message(Message.TYPE_CHAT_JOIN);
        JsonObject payload = new JsonObject();
        payload.addProperty("userId", userId);
        payload.addProperty("name", name);
        message.setPayload(payload);

        String json = message.toJson();

        // Send to all chat clients
        for (ClientInfo client : clients.values()) {
            if ("chat".equals(client.getClientType()) && client.getChannel().isActive()) {
                client.getChannel().writeAndFlush(new TextWebSocketFrame(json));
            }
        }
    }
    
    /**
     * Broadcast chat leave notification.
     */
    public void broadcastChatLeave(String userId, String name) {
        Message message = new Message(Message.TYPE_CHAT_LEAVE);
        JsonObject payload = new JsonObject();
        payload.addProperty("userId", userId);
        payload.addProperty("name", name);
        message.setPayload(payload);

        String json = message.toJson();

        // Send to all chat clients
        for (ClientInfo client : clients.values()) {
            if ("chat".equals(client.getClientType()) && client.getChannel().isActive()) {
                client.getChannel().writeAndFlush(new TextWebSocketFrame(json));
            }
        }
    }
    
    /**
     * Broadcast chat message to all chat clients.
     */
    public void broadcastChatMessage(JsonObject msgPayload, Channel senderChannel) {
        Message message = new Message(Message.TYPE_CHAT_MESSAGE);
        message.setPayload(msgPayload);

        String json = message.toJson();
        
        // Check if there are specific recipients
        JsonArray recipients = null;
        if (msgPayload.has("recipients") && !msgPayload.get("recipients").isJsonNull()) {
            recipients = msgPayload.getAsJsonArray("recipients");
        }

        // Send to all chat clients (or specific recipients)
        for (ClientInfo client : clients.values()) {
            if (!"chat".equals(client.getClientType()) || !client.getChannel().isActive()) {
                continue;
            }
            
            // Don't send back to sender
            if (client.getChannel() == senderChannel) {
                continue;
            }
            
            // If recipients specified, only send to them
            if (recipients != null) {
                boolean isRecipient = false;
                for (int i = 0; i < recipients.size(); i++) {
                    if (client.getUserId().equals(recipients.get(i).getAsString())) {
                        isRecipient = true;
                        break;
                    }
                }
                if (!isRecipient) {
                    continue;
                }
            }
            
            client.getChannel().writeAndFlush(new TextWebSocketFrame(json));
        }
    }
}
