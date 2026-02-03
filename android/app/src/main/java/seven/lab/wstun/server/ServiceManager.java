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
 * Manages registered services and their WebSocket connections.
 */
public class ServiceManager {
    
    private static final String TAG = "ServiceManager";

    // Service name -> ServiceEntry
    private final Map<String, ServiceEntry> services = new ConcurrentHashMap<>();
    
    // Channel -> Service name (for cleanup on disconnect)
    private final Map<Channel, String> channelToService = new ConcurrentHashMap<>();

    // File registry: fileId -> FileInfo (for relay file sharing)
    private final Map<String, FileInfo> fileRegistry = new ConcurrentHashMap<>();
    
    // Channel -> List of file IDs owned by that channel
    private final Map<Channel, List<String>> channelToFiles = new ConcurrentHashMap<>();

    private ServiceChangeListener listener;
    private RequestManager requestManager;

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

        public ServiceEntry(ServiceRegistration registration, Channel channel) {
            this.name = registration.getName();
            this.type = registration.getType();
            this.description = registration.getDescription();
            this.channel = channel;
            this.registration = registration;
            this.registeredAt = System.currentTimeMillis();
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
    }

    public void setListener(ServiceChangeListener listener) {
        this.listener = listener;
    }

    public void setRequestManager(RequestManager requestManager) {
        this.requestManager = requestManager;
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
     * Handle channel disconnect - unregister associated service and clean up files.
     */
    public void onChannelDisconnect(Channel channel) {
        // Clean up files owned by this channel
        cleanupFilesForChannel(channel);
        
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

        // Send to all fileshare service channels
        ServiceEntry fileshare = services.get("fileshare");
        if (fileshare != null && fileshare.isConnected()) {
            fileshare.getChannel().writeAndFlush(new TextWebSocketFrame(json));
        }

        // Also send to any other channels that have registered files
        for (Channel channel : channelToFiles.keySet()) {
            if (channel.isActive()) {
                channel.writeAndFlush(new TextWebSocketFrame(json));
            }
        }
    }
}
