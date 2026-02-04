package seven.lab.wstun.server;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import seven.lab.wstun.config.ServerConfig;
import seven.lab.wstun.marketplace.InstalledService;
import seven.lab.wstun.marketplace.MarketplaceService;
import seven.lab.wstun.marketplace.ServiceManifest;

/**
 * Manages local services including built-in and marketplace-installed services.
 * Services can be enabled/disabled and have endpoints attached/detached dynamically.
 */
public class LocalServiceManager {
    
    private static final String TAG = "LocalServiceManager";
    private static final Gson gson = new Gson();

    private final Context context;
    private final ServerConfig config;
    private final MarketplaceService marketplaceService;
    
    // Service status tracking (for running instances)
    private final Map<String, ServiceStatus> serviceStatuses = new ConcurrentHashMap<>();
    
    // HTML/JS content cache
    private String libwstunJs;
    
    /**
     * Represents the status of a local service.
     */
    public static class ServiceStatus {
        private final String serviceName;
        private String uuid;
        private boolean running;
        private long startedAt;
        private long stoppedAt;
        
        public ServiceStatus(String serviceName) {
            this.serviceName = serviceName;
            this.running = false;
            this.uuid = null;
        }
        
        public void start() {
            this.uuid = UUID.randomUUID().toString();
            this.running = true;
            this.startedAt = System.currentTimeMillis();
            this.stoppedAt = 0;
        }
        
        public void stop() {
            this.running = false;
            this.stoppedAt = System.currentTimeMillis();
            // Keep UUID so we can identify this was our service
        }
        
        public String getServiceName() { return serviceName; }
        public String getUuid() { return uuid; }
        public boolean isRunning() { return running; }
        public long getStartedAt() { return startedAt; }
        public long getStoppedAt() { return stoppedAt; }
        
        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("serviceName", serviceName);
            obj.addProperty("uuid", uuid);
            obj.addProperty("running", running);
            obj.addProperty("startedAt", startedAt);
            obj.addProperty("stoppedAt", stoppedAt);
            return obj;
        }
    }
    
    public LocalServiceManager(Context context, ServerConfig config) {
        this.context = context;
        this.config = config;
        this.marketplaceService = new MarketplaceService(context);
        
        // Initialize service statuses for all installed services
        for (String name : marketplaceService.getInstalledServices().keySet()) {
            serviceStatuses.put(name, new ServiceStatus(name));
        }
        
        // Load libwstun.js
        loadLibwstunJs();
    }
    
    private void loadLibwstunJs() {
        try {
            libwstunJs = loadAsset("libwstun.js");
            Log.i(TAG, "Loaded libwstun.js from assets");
        } catch (IOException e) {
            Log.e(TAG, "Failed to load assets", e);
            libwstunJs = "// libwstun.js failed to load";
        }
    }
    
    /**
     * Generate the admin HTML page dynamically.
     */
    public String getAdminHtml() {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<title>WSTun Admin</title><style>");
        html.append("body{font-family:Arial,sans-serif;margin:0;padding:20px;background:#f5f5f5;}");
        html.append(".card{background:white;padding:20px;border-radius:8px;margin-bottom:16px;box-shadow:0 2px 4px rgba(0,0,0,0.1);}");
        html.append("h1{color:#6200ee;}h2{color:#333;margin-top:0;}");
        html.append("a{color:#6200ee;}.btn{display:inline-block;padding:10px 20px;background:#6200ee;color:white;text-decoration:none;border-radius:6px;margin-right:8px;margin-bottom:8px;}");
        html.append(".btn:hover{background:#3700b3;}.badge{display:inline-block;padding:4px 8px;border-radius:4px;font-size:12px;margin-left:8px;}");
        html.append(".enabled{background:#e8f5e9;color:#2e7d32;}.disabled{background:#f5f5f5;color:#666;}");
        html.append("</style></head><body>");
        html.append("<h1>WSTun Service Manager</h1>");
        html.append("<p>Use the WSTun Android app to manage services, or access services directly below.</p>");
        
        html.append("<div class='card'><h2>Installed Services</h2>");
        for (Map.Entry<String, InstalledService> entry : getInstalledServices().entrySet()) {
            String name = entry.getKey();
            InstalledService svc = entry.getValue();
            String displayName = svc.getDisplayName() != null ? svc.getDisplayName() : name;
            html.append("<p><strong>").append(displayName).append("</strong>");
            html.append("<span class='badge ").append(svc.isEnabled() ? "enabled" : "disabled").append("'>");
            html.append(svc.isEnabled() ? "Enabled" : "Disabled").append("</span>");
            if (svc.isEnabled()) {
                html.append(" - <a class='btn' href='/").append(name).append("/service'>Manage</a>");
            }
            html.append("</p>");
        }
        html.append("</div>");
        
        html.append("</body></html>");
        return html.toString();
    }
    
    private String loadAsset(String path) throws IOException {
        InputStream is = context.getAssets().open(path);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }
    
    /**
     * Check if a service is enabled.
     */
    public boolean isServiceEnabled(String serviceName) {
        InstalledService service = marketplaceService.getInstalledService(serviceName);
        return service != null && service.isEnabled();
    }
    
    /**
     * Get the marketplace service.
     */
    public MarketplaceService getMarketplaceService() {
        return marketplaceService;
    }
    
    /**
     * Get all installed services.
     */
    public Map<String, InstalledService> getInstalledServices() {
        return marketplaceService.getInstalledServices();
    }
    
    /**
     * Get installed service by name.
     */
    public InstalledService getInstalledService(String serviceName) {
        return marketplaceService.getInstalledService(serviceName);
    }
    
    /**
     * Enable a service.
     */
    public boolean enableService(String serviceName) {
        boolean result = marketplaceService.enableService(serviceName);
        if (result && !serviceStatuses.containsKey(serviceName)) {
            serviceStatuses.put(serviceName, new ServiceStatus(serviceName));
        }
        return result;
    }
    
    /**
     * Disable a service.
     */
    public boolean disableService(String serviceName, ServiceManager serviceManager) {
        InstalledService service = marketplaceService.getInstalledService(serviceName);
        if (service == null) {
            return false;
        }
        
        // Close all instances for this service
        if (serviceManager != null) {
            serviceManager.closeInstancesForService(serviceName);
        }
        
        // Stop the service status
        ServiceStatus status = serviceStatuses.get(serviceName);
        if (status != null && status.isRunning()) {
            status.stop();
        }
        
        return marketplaceService.disableService(serviceName);
    }
    
    /**
     * Uninstall a service.
     */
    public boolean uninstallService(String serviceName, ServiceManager serviceManager) {
        // Disable first
        disableService(serviceName, serviceManager);
        
        // Remove status
        serviceStatuses.remove(serviceName);
        
        return marketplaceService.uninstallService(serviceName);
    }
    
    /**
     * Get service status.
     */
    public ServiceStatus getServiceStatus(String serviceName) {
        return serviceStatuses.get(serviceName);
    }
    
    /**
     * Start a local service.
     */
    public boolean startService(String serviceName) {
        if (!isServiceEnabled(serviceName)) {
            Log.w(TAG, "Service not enabled: " + serviceName);
            return false;
        }
        
        ServiceStatus status = serviceStatuses.get(serviceName);
        if (status == null) {
            Log.w(TAG, "Unknown service: " + serviceName);
            return false;
        }
        
        if (status.isRunning()) {
            Log.w(TAG, "Service already running: " + serviceName);
            return false;
        }
        
        status.start();
        Log.i(TAG, "Started local service: " + serviceName + " (UUID: " + status.getUuid() + ")");
        return true;
    }
    
    /**
     * Stop a local service.
     */
    public boolean stopService(String serviceName) {
        ServiceStatus status = serviceStatuses.get(serviceName);
        if (status == null) {
            Log.w(TAG, "Unknown service: " + serviceName);
            return false;
        }
        
        if (!status.isRunning()) {
            Log.w(TAG, "Service not running: " + serviceName);
            return false;
        }
        
        status.stop();
        Log.i(TAG, "Stopped local service: " + serviceName);
        return true;
    }
    
    /**
     * Stop a service by UUID (ensures it's exactly our service).
     */
    public boolean stopServiceByUuid(String serviceName, String uuid) {
        ServiceStatus status = serviceStatuses.get(serviceName);
        if (status == null) {
            return false;
        }
        
        if (!uuid.equals(status.getUuid())) {
            Log.w(TAG, "UUID mismatch for service: " + serviceName);
            return false;
        }
        
        return stopService(serviceName);
    }
    
    /**
     * Get the service management HTML page for a service.
     * This returns the service controller page (index.html) which:
     * - Registers as a SERVICE with the server
     * - Shows in Android UI
     * - Manages user clients
     */
    public String getServicePageHtml(String serviceName, String serverUrl) {
        if (!isServiceEnabled(serviceName)) {
            return getServiceDisabledHtml(serviceName);
        }
        
        InstalledService service = marketplaceService.getInstalledService(serviceName);
        if (service == null) {
            return getServiceDisabledHtml(serviceName);
        }
        
        // Get the /service endpoint file
        String content = service.getFileContent("/service");
        if (content == null || content.isEmpty()) {
            return getServiceDisabledHtml(serviceName);
        }
        return content;
    }
    
    /**
     * Get the main service HTML (the user client UI).
     * This is served directly from server when service is registered.
     */
    public String getServiceMainHtml(String serviceName) {
        InstalledService service = marketplaceService.getInstalledService(serviceName);
        if (service == null) {
            return null;
        }
        
        return service.getFileContent("/main");
    }
    
    /**
     * Get file content for any endpoint of a service.
     */
    public String getServiceFileContent(String serviceName, String path) {
        InstalledService service = marketplaceService.getInstalledService(serviceName);
        if (service == null || !service.isEnabled()) {
            return null;
        }
        
        return service.getFileContent(path);
    }
    
    /**
     * Get list of all endpoints for a service.
     */
    public List<ServiceManifest.Endpoint> getServiceEndpoints(String serviceName) {
        InstalledService service = marketplaceService.getInstalledService(serviceName);
        if (service == null || service.getManifest() == null) {
            return new ArrayList<>();
        }
        return service.getManifest().getEndpoints();
    }
    
    /**
     * Get the libwstun.js library content.
     */
    public String getLibWstunJs() {
        return libwstunJs;
    }
    
    private String getServiceDisabledHtml(String serviceName) {
        InstalledService service = marketplaceService.getInstalledService(serviceName);
        String displayName = service != null ? service.getDisplayName() : serviceName;
        
        return "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
            "    <title>" + displayName + " Service - Disabled</title>\n" +
            "    <style>\n" +
            "body { font-family: Arial, sans-serif; background: #f5f5f5; min-height: 100vh; display: flex; align-items: center; justify-content: center; }\n" +
            ".card { background: white; padding: 40px; border-radius: 12px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); text-align: center; max-width: 400px; }\n" +
            "h1 { color: #f44336; margin-bottom: 16px; }\n" +
            "p { color: #666; }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"card\">\n" +
            "        <h1>Service Disabled</h1>\n" +
            "        <p>The " + displayName + " service is not enabled on this server.</p>\n" +
            "        <p>Enable it in the WSTun app service management.</p>\n" +
            "    </div>\n" +
            "</body>\n" +
            "</html>";
    }
    
    /**
     * Get all installed services as JSON array.
     */
    public JsonArray getInstalledServicesJson() {
        JsonArray arr = new JsonArray();
        for (Map.Entry<String, InstalledService> entry : marketplaceService.getInstalledServices().entrySet()) {
            JsonObject obj = entry.getValue().toJson();
            obj.addProperty("name", entry.getKey());
            
            // Add running status
            ServiceStatus status = serviceStatuses.get(entry.getKey());
            if (status != null) {
                obj.addProperty("running", status.isRunning());
                if (status.getUuid() != null) {
                    obj.addProperty("uuid", status.getUuid());
                }
            }
            arr.add(obj);
        }
        return arr;
    }
    
    /**
     * Shutdown resources.
     */
    public void shutdown() {
        if (marketplaceService != null) {
            marketplaceService.shutdown();
        }
    }
}
