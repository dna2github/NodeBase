package seven.lab.wstun.marketplace;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * Represents a service manifest downloaded from marketplace.
 * The manifest defines service metadata and files to be downloaded.
 * 
 * Example manifest JSON:
 * {
 *   "name": "test",
 *   "displayName": "Test Service",
 *   "description": "A test service for demonstration",
 *   "version": "1.0.0",
 *   "author": "Example Author",
 *   "endpoints": [
 *     {"path": "/service", "file": "index.html", "type": "service"},
 *     {"path": "/main", "file": "main.html", "type": "client"}
 *   ]
 * }
 */
public class ServiceManifest {
    
    private static final Gson gson = new Gson();
    
    @SerializedName("name")
    private String name;
    
    @SerializedName("displayName")
    private String displayName;
    
    @SerializedName("description")
    private String description;
    
    @SerializedName("version")
    private String version;
    
    @SerializedName("author")
    private String author;
    
    @SerializedName("icon")
    private String icon;
    
    @SerializedName("endpoints")
    private List<Endpoint> endpoints;
    
    /**
     * Represents an endpoint defined by the service.
     */
    public static class Endpoint {
        @SerializedName("path")
        private String path;  // e.g., "/service", "/main"
        
        @SerializedName("file")
        private String file;  // e.g., "index.html", "main.html"
        
        @SerializedName("type")
        private String type;  // "service" or "client"
        
        @SerializedName("contentType")
        private String contentType;  // optional, defaults based on file extension
        
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        
        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        
        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("path", path);
            obj.addProperty("file", file);
            obj.addProperty("type", type);
            if (contentType != null) {
                obj.addProperty("contentType", contentType);
            }
            return obj;
        }
    }
    
    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDisplayName() { return displayName != null ? displayName : name; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    
    public List<Endpoint> getEndpoints() { return endpoints; }
    public void setEndpoints(List<Endpoint> endpoints) { this.endpoints = endpoints; }
    
    /**
     * Parse manifest from JSON string.
     */
    public static ServiceManifest fromJson(String json) {
        return gson.fromJson(json, ServiceManifest.class);
    }
    
    /**
     * Convert to JSON string.
     */
    public String toJsonString() {
        return gson.toJson(this);
    }
    
    /**
     * Convert to JsonObject for API responses.
     */
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", name);
        obj.addProperty("displayName", getDisplayName());
        obj.addProperty("description", description);
        obj.addProperty("version", version);
        obj.addProperty("author", author);
        if (icon != null) {
            obj.addProperty("icon", icon);
        }
        if (endpoints != null) {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (Endpoint ep : endpoints) {
                arr.add(ep.toJson());
            }
            obj.add("endpoints", arr);
        }
        return obj;
    }
}
