package seven.lab.wstun.marketplace;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents an installed service with its manifest and files.
 */
public class InstalledService {
    
    private static final Gson gson = new Gson();
    
    @SerializedName("manifest")
    private ServiceManifest manifest;
    
    @SerializedName("enabled")
    private boolean enabled;
    
    @SerializedName("installedAt")
    private long installedAt;
    
    @SerializedName("source")
    private String source;  // marketplace URL or "builtin"
    
    // Runtime state (not persisted)
    private transient Map<String, String> files = new ConcurrentHashMap<>();  // path -> content
    private transient boolean running;
    
    public InstalledService() {
        this.enabled = false;
        this.running = false;
        this.installedAt = System.currentTimeMillis();
    }
    
    public InstalledService(ServiceManifest manifest, String source) {
        this();
        this.manifest = manifest;
        this.source = source;
    }
    
    // Getters and setters
    public ServiceManifest getManifest() { return manifest; }
    public void setManifest(ServiceManifest manifest) { this.manifest = manifest; }
    
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public long getInstalledAt() { return installedAt; }
    public void setInstalledAt(long installedAt) { this.installedAt = installedAt; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public Map<String, String> getFiles() { return files; }
    public void setFiles(Map<String, String> files) { this.files = files; }
    
    public boolean isRunning() { return running; }
    public void setRunning(boolean running) { this.running = running; }
    
    /**
     * Get file content for an endpoint path.
     */
    public String getFileContent(String path) {
        if (manifest == null || manifest.getEndpoints() == null) {
            return null;
        }
        
        for (ServiceManifest.Endpoint ep : manifest.getEndpoints()) {
            if (path.equals(ep.getPath())) {
                return files.get(ep.getFile());
            }
        }
        return null;
    }
    
    /**
     * Get service name.
     */
    public String getName() {
        return manifest != null ? manifest.getName() : null;
    }
    
    /**
     * Get display name.
     */
    public String getDisplayName() {
        return manifest != null ? manifest.getDisplayName() : null;
    }
    
    /**
     * Convert to JSON for API.
     */
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        if (manifest != null) {
            obj.add("manifest", manifest.toJson());
        }
        obj.addProperty("enabled", enabled);
        obj.addProperty("running", running);
        obj.addProperty("installedAt", installedAt);
        obj.addProperty("source", source);
        return obj;
    }
    
    /**
     * Convert to JSON string for persistence.
     */
    public String toJsonString() {
        return gson.toJson(this);
    }
    
    /**
     * Parse from JSON string.
     */
    public static InstalledService fromJson(String json) {
        return gson.fromJson(json, InstalledService.class);
    }
}
