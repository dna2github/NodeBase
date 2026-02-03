package seven.lab.wstun.server;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import seven.lab.wstun.config.ServerConfig;

/**
 * Manages built-in local services (FileShare, Chat) that are hosted by the server itself.
 * These services are loaded from assets and can be started/stopped via HTTP API.
 */
public class LocalServiceManager {
    
    private static final String TAG = "LocalServiceManager";
    private static final Gson gson = new Gson();

    private final Context context;
    private final ServerConfig config;
    
    // Service status tracking
    private final Map<String, ServiceStatus> serviceStatuses = new ConcurrentHashMap<>();
    
    // HTML content cache
    private String fileshareHtml;
    private String chatHtml;
    
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
        
        // Initialize service statuses
        serviceStatuses.put("fileshare", new ServiceStatus("fileshare"));
        serviceStatuses.put("chat", new ServiceStatus("chat"));
        
        // Load HTML content from assets
        loadServiceHtml();
    }
    
    private void loadServiceHtml() {
        try {
            fileshareHtml = loadAsset("services/fileshare/index.html");
            chatHtml = loadAsset("services/chat/index.html");
            Log.i(TAG, "Loaded service HTML from assets");
        } catch (IOException e) {
            Log.e(TAG, "Failed to load service HTML from assets", e);
            // Use embedded HTML as fallback
            fileshareHtml = getEmbeddedFileshareHtml();
            chatHtml = getEmbeddedChatHtml();
        }
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
     * Check if a local service is enabled in config.
     */
    public boolean isServiceEnabled(String serviceName) {
        if ("fileshare".equals(serviceName)) {
            return config.isFileshareEnabled();
        } else if ("chat".equals(serviceName)) {
            return config.isChatEnabled();
        }
        return false;
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
     */
    public String getServicePageHtml(String serviceName, String serverUrl) {
        ServiceStatus status = serviceStatuses.get(serviceName);
        if (status == null || !isServiceEnabled(serviceName)) {
            return getServiceDisabledHtml(serviceName);
        }
        
        return generateServicePageHtml(serviceName, serverUrl, status);
    }
    
    /**
     * Get the main service HTML (the actual fileshare/chat UI).
     */
    public String getServiceMainHtml(String serviceName) {
        if ("fileshare".equals(serviceName)) {
            return fileshareHtml;
        } else if ("chat".equals(serviceName)) {
            return chatHtml;
        }
        return null;
    }
    
    private String generateServicePageHtml(String serviceName, String serverUrl, ServiceStatus status) {
        String displayName = "fileshare".equals(serviceName) ? "FileShare" : "Chat";
        String mainPath = "/" + serviceName + "/main";
        
        return "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
            "    <title>" + displayName + " Service - WSTun</title>\n" +
            "    <style>\n" +
            "* { box-sizing: border-box; margin: 0; padding: 0; }\n" +
            "body { font-family: Arial, sans-serif; background: #f5f5f5; min-height: 100vh; padding: 20px; }\n" +
            ".container { max-width: 600px; margin: 0 auto; }\n" +
            ".card { background: white; padding: 24px; border-radius: 12px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); margin-bottom: 20px; }\n" +
            "h1 { color: #6200ee; margin-bottom: 8px; }\n" +
            ".subtitle { color: #666; margin-bottom: 20px; }\n" +
            ".status { padding: 16px; border-radius: 8px; margin-bottom: 20px; }\n" +
            ".status.running { background: #e8f5e9; border: 1px solid #4caf50; }\n" +
            ".status.stopped { background: #fff3e0; border: 1px solid #ff9800; }\n" +
            ".status-label { font-weight: bold; margin-bottom: 8px; }\n" +
            ".status.running .status-label { color: #2e7d32; }\n" +
            ".status.stopped .status-label { color: #e65100; }\n" +
            ".status-info { font-size: 14px; color: #666; }\n" +
            ".status-info code { background: #f5f5f5; padding: 2px 6px; border-radius: 4px; font-size: 12px; }\n" +
            ".buttons { display: flex; gap: 12px; flex-wrap: wrap; }\n" +
            ".btn { padding: 12px 24px; border: none; border-radius: 8px; font-size: 16px; cursor: pointer; transition: all 0.2s; }\n" +
            ".btn:disabled { opacity: 0.5; cursor: not-allowed; }\n" +
            ".btn-primary { background: #6200ee; color: white; }\n" +
            ".btn-primary:hover:not(:disabled) { background: #3700b3; }\n" +
            ".btn-danger { background: #f44336; color: white; }\n" +
            ".btn-danger:hover:not(:disabled) { background: #d32f2f; }\n" +
            ".btn-secondary { background: #e0e0e0; color: #333; }\n" +
            ".btn-secondary:hover:not(:disabled) { background: #bdbdbd; }\n" +
            ".link-section { margin-top: 20px; padding-top: 20px; border-top: 1px solid #eee; }\n" +
            ".link-section a { color: #6200ee; text-decoration: none; }\n" +
            ".link-section a:hover { text-decoration: underline; }\n" +
            ".message { padding: 12px; border-radius: 8px; margin-top: 16px; display: none; }\n" +
            ".message.success { background: #e8f5e9; color: #2e7d32; display: block; }\n" +
            ".message.error { background: #ffebee; color: #c62828; display: block; }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"container\">\n" +
            "        <div class=\"card\">\n" +
            "            <h1>" + displayName + " Service</h1>\n" +
            "            <p class=\"subtitle\">Manage the built-in " + displayName.toLowerCase() + " service</p>\n" +
            "            \n" +
            "            <div id=\"statusBox\" class=\"status stopped\">\n" +
            "                <div class=\"status-label\" id=\"statusLabel\">Loading...</div>\n" +
            "                <div class=\"status-info\" id=\"statusInfo\"></div>\n" +
            "            </div>\n" +
            "            \n" +
            "            <div class=\"buttons\">\n" +
            "                <button id=\"startBtn\" class=\"btn btn-primary\" onclick=\"startService()\" disabled>Start Service</button>\n" +
            "                <button id=\"stopBtn\" class=\"btn btn-danger\" onclick=\"stopService()\" disabled>Stop Service</button>\n" +
            "                <button id=\"openBtn\" class=\"btn btn-secondary\" onclick=\"openService()\" disabled>Open " + displayName + "</button>\n" +
            "            </div>\n" +
            "            \n" +
            "            <div id=\"message\" class=\"message\"></div>\n" +
            "            \n" +
            "            <div class=\"link-section\">\n" +
            "                <p>Service URL: <a href=\"" + mainPath + "\" id=\"serviceLink\">" + serverUrl + mainPath + "</a></p>\n" +
            "            </div>\n" +
            "        </div>\n" +
            "    </div>\n" +
            "    \n" +
            "    <script>\n" +
            "const SERVICE_NAME = '" + serviceName + "';\n" +
            "let currentUuid = null;\n" +
            "\n" +
            "async function loadStatus() {\n" +
            "    try {\n" +
            "        const res = await fetch('/' + SERVICE_NAME + '/service/api/status');\n" +
            "        const data = await res.json();\n" +
            "        updateUI(data);\n" +
            "    } catch (err) {\n" +
            "        showMessage('Failed to load status: ' + err.message, 'error');\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "function updateUI(status) {\n" +
            "    const statusBox = document.getElementById('statusBox');\n" +
            "    const statusLabel = document.getElementById('statusLabel');\n" +
            "    const statusInfo = document.getElementById('statusInfo');\n" +
            "    const startBtn = document.getElementById('startBtn');\n" +
            "    const stopBtn = document.getElementById('stopBtn');\n" +
            "    const openBtn = document.getElementById('openBtn');\n" +
            "    \n" +
            "    currentUuid = status.uuid;\n" +
            "    \n" +
            "    if (status.running) {\n" +
            "        statusBox.className = 'status running';\n" +
            "        statusLabel.textContent = 'Service Running';\n" +
            "        statusInfo.innerHTML = 'UUID: <code>' + status.uuid + '</code><br>Started: ' + new Date(status.startedAt).toLocaleString();\n" +
            "        startBtn.disabled = true;\n" +
            "        stopBtn.disabled = false;\n" +
            "        openBtn.disabled = false;\n" +
            "    } else {\n" +
            "        statusBox.className = 'status stopped';\n" +
            "        statusLabel.textContent = 'Service Stopped';\n" +
            "        if (status.stoppedAt > 0) {\n" +
            "            statusInfo.innerHTML = 'Last UUID: <code>' + (status.uuid || 'none') + '</code><br>Stopped: ' + new Date(status.stoppedAt).toLocaleString();\n" +
            "        } else {\n" +
            "            statusInfo.textContent = 'Service has not been started yet';\n" +
            "        }\n" +
            "        startBtn.disabled = false;\n" +
            "        stopBtn.disabled = true;\n" +
            "        openBtn.disabled = true;\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "async function startService() {\n" +
            "    try {\n" +
            "        const res = await fetch('/' + SERVICE_NAME + '/service/api/start', { method: 'POST' });\n" +
            "        const data = await res.json();\n" +
            "        if (data.success) {\n" +
            "            showMessage('Service started successfully!', 'success');\n" +
            "            loadStatus();\n" +
            "        } else {\n" +
            "            showMessage('Failed to start: ' + (data.error || 'Unknown error'), 'error');\n" +
            "        }\n" +
            "    } catch (err) {\n" +
            "        showMessage('Failed to start service: ' + err.message, 'error');\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "async function stopService() {\n" +
            "    try {\n" +
            "        const res = await fetch('/' + SERVICE_NAME + '/service/api/stop', {\n" +
            "            method: 'POST',\n" +
            "            headers: { 'Content-Type': 'application/json' },\n" +
            "            body: JSON.stringify({ uuid: currentUuid })\n" +
            "        });\n" +
            "        const data = await res.json();\n" +
            "        if (data.success) {\n" +
            "            showMessage('Service stopped successfully!', 'success');\n" +
            "            loadStatus();\n" +
            "        } else {\n" +
            "            showMessage('Failed to stop: ' + (data.error || 'Unknown error'), 'error');\n" +
            "        }\n" +
            "    } catch (err) {\n" +
            "        showMessage('Failed to stop service: ' + err.message, 'error');\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "function openService() {\n" +
            "    window.open('/' + SERVICE_NAME + '/main', '_blank');\n" +
            "}\n" +
            "\n" +
            "function showMessage(text, type) {\n" +
            "    const msg = document.getElementById('message');\n" +
            "    msg.textContent = text;\n" +
            "    msg.className = 'message ' + type;\n" +
            "    setTimeout(() => { msg.className = 'message'; }, 5000);\n" +
            "}\n" +
            "\n" +
            "// Load status on page load and refresh periodically\n" +
            "loadStatus();\n" +
            "setInterval(loadStatus, 5000);\n" +
            "    </script>\n" +
            "</body>\n" +
            "</html>";
    }
    
    private String getServiceDisabledHtml(String serviceName) {
        String displayName = "fileshare".equals(serviceName) ? "FileShare" : "Chat";
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
            "        <p>Enable it in the WSTun app configuration.</p>\n" +
            "    </div>\n" +
            "</body>\n" +
            "</html>";
    }
    
    // Fallback embedded HTML for FileShare
    private String getEmbeddedFileshareHtml() {
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>FileShare</title></head><body><h1>FileShare Service</h1><p>Service HTML not loaded.</p></body></html>";
    }
    
    // Fallback embedded HTML for Chat
    private String getEmbeddedChatHtml() {
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>Chat</title></head><body><h1>Chat Service</h1><p>Service HTML not loaded.</p></body></html>";
    }
}
