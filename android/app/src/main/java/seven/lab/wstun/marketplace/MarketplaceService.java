package seven.lab.wstun.marketplace;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages marketplace operations: browsing, downloading, installing services.
 * 
 * Marketplace API:
 * - GET {baseUrl}/list - Returns JSON array of available services with name, description
 * - GET {baseUrl}/service/{name}.json - Returns service manifest
 * - GET {baseUrl}/service/{name}/{file} - Downloads service file
 */
public class MarketplaceService {
    
    private static final String TAG = "MarketplaceService";
    private static final String PREFS_NAME = "wstun_marketplace";
    private static final String KEY_INSTALLED_SERVICES = "installed_services";
    private static final String KEY_MARKETPLACE_URL = "marketplace_url";
    private static final Gson gson = new Gson();
    
    private final Context context;
    private final File servicesDir;
    private final SharedPreferences prefs;
    private final ExecutorService executor;
    
    // Installed services cache
    private final Map<String, InstalledService> installedServices = new ConcurrentHashMap<>();
    
    // Callback interfaces
    public interface MarketplaceCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }
    
    public MarketplaceService(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.executor = Executors.newCachedThreadPool();
        
        // Create services directory
        this.servicesDir = new File(context.getFilesDir(), "services");
        if (!servicesDir.exists()) {
            servicesDir.mkdirs();
        }
        
        // Load installed services
        loadInstalledServices();
        
        // Load built-in services
        loadBuiltinServices();
    }
    
    /**
     * Load installed services from storage.
     */
    private void loadInstalledServices() {
        String json = prefs.getString(KEY_INSTALLED_SERVICES, "{}");
        try {
            Type type = new TypeToken<Map<String, InstalledService>>(){}.getType();
            Map<String, InstalledService> loaded = gson.fromJson(json, type);
            if (loaded != null) {
                installedServices.putAll(loaded);
            }
            
            // Load file contents from disk
            for (InstalledService service : installedServices.values()) {
                loadServiceFiles(service);
            }
            
            Log.i(TAG, "Loaded " + installedServices.size() + " installed services");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load installed services", e);
        }
    }
    
    /**
     * Load built-in services (fileshare, chat) as installed services.
     */
    private void loadBuiltinServices() {
        // Load fileshare
        if (!installedServices.containsKey("fileshare")) {
            InstalledService fileshare = createBuiltinService("fileshare", "FileShare", 
                "Share files with others in real-time");
            installedServices.put("fileshare", fileshare);
        }
        
        // Load chat
        if (!installedServices.containsKey("chat")) {
            InstalledService chat = createBuiltinService("chat", "Chat",
                "Real-time chat rooms");
            installedServices.put("chat", chat);
        }
        
        // Load built-in HTML from assets
        loadBuiltinAssets();
    }
    
    /**
     * Create a built-in service entry.
     */
    private InstalledService createBuiltinService(String name, String displayName, String description) {
        ServiceManifest manifest = new ServiceManifest();
        manifest.setName(name);
        manifest.setDisplayName(displayName);
        manifest.setDescription(description);
        manifest.setVersion("1.0.0");
        manifest.setAuthor("Built-in");
        
        List<ServiceManifest.Endpoint> endpoints = new ArrayList<>();
        
        ServiceManifest.Endpoint serviceEp = new ServiceManifest.Endpoint();
        serviceEp.setPath("/service");
        serviceEp.setFile("index.html");
        serviceEp.setType("service");
        endpoints.add(serviceEp);
        
        ServiceManifest.Endpoint mainEp = new ServiceManifest.Endpoint();
        mainEp.setPath("/main");
        mainEp.setFile("main.html");
        mainEp.setType("client");
        endpoints.add(mainEp);
        
        manifest.setEndpoints(endpoints);
        
        InstalledService service = new InstalledService(manifest, "builtin");
        service.setInstalledAt(0);  // Built-in
        service.setEnabled(false);  // Disabled by default - user enables on the fly
        return service;
    }
    
    /**
     * Load built-in HTML assets.
     */
    private void loadBuiltinAssets() {
        try {
            InstalledService fileshare = installedServices.get("fileshare");
            if (fileshare != null && fileshare.getFiles().isEmpty()) {
                fileshare.getFiles().put("index.html", loadAsset("services/fileshare/index.html"));
                fileshare.getFiles().put("main.html", loadAsset("services/fileshare/main.html"));
            }
            
            InstalledService chat = installedServices.get("chat");
            if (chat != null && chat.getFiles().isEmpty()) {
                chat.getFiles().put("index.html", loadAsset("services/chat/index.html"));
                chat.getFiles().put("main.html", loadAsset("services/chat/main.html"));
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to load built-in assets", e);
        }
    }
    
    private String loadAsset(String path) throws IOException {
        InputStream is = context.getAssets().open(path);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }
    
    /**
     * Save installed services to storage.
     */
    private void saveInstalledServices() {
        try {
            // Create a copy without file contents for storage
            Map<String, InstalledService> toSave = new ConcurrentHashMap<>();
            for (Map.Entry<String, InstalledService> entry : installedServices.entrySet()) {
                if (!"builtin".equals(entry.getValue().getSource())) {
                    toSave.put(entry.getKey(), entry.getValue());
                }
            }
            String json = gson.toJson(toSave);
            prefs.edit().putString(KEY_INSTALLED_SERVICES, json).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save installed services", e);
        }
    }
    
    /**
     * Load service files from disk.
     */
    private void loadServiceFiles(InstalledService service) {
        if (service == null || service.getManifest() == null) return;
        if ("builtin".equals(service.getSource())) return;  // Built-ins loaded from assets
        
        File serviceDir = new File(servicesDir, service.getName());
        if (!serviceDir.exists()) return;
        
        List<ServiceManifest.Endpoint> endpoints = service.getManifest().getEndpoints();
        if (endpoints == null) return;
        
        for (ServiceManifest.Endpoint ep : endpoints) {
            File file = new File(serviceDir, ep.getFile());
            if (file.exists()) {
                try {
                    String content = readFile(file);
                    service.getFiles().put(ep.getFile(), content);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to load file: " + file.getPath(), e);
                }
            }
        }
    }
    
    private String readFile(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }
    
    /**
     * Get or set marketplace URL.
     */
    public String getMarketplaceUrl() {
        return prefs.getString(KEY_MARKETPLACE_URL, "");
    }
    
    public void setMarketplaceUrl(String url) {
        prefs.edit().putString(KEY_MARKETPLACE_URL, url).apply();
    }
    
    /**
     * List services from marketplace.
     */
    public void listMarketplace(String baseUrl, MarketplaceCallback<List<JsonObject>> callback) {
        executor.execute(() -> {
            try {
                String listUrl = baseUrl.endsWith("/") ? baseUrl + "list" : baseUrl + "/list";
                String json = httpGet(listUrl);
                JsonArray arr = gson.fromJson(json, JsonArray.class);
                
                List<JsonObject> services = new ArrayList<>();
                for (JsonElement el : arr) {
                    services.add(el.getAsJsonObject());
                }
                
                callback.onSuccess(services);
            } catch (Exception e) {
                Log.e(TAG, "Failed to list marketplace", e);
                callback.onError("Failed to fetch marketplace: " + e.getMessage());
            }
        });
    }
    
    /**
     * Get service manifest from marketplace.
     */
    public void getServiceManifest(String baseUrl, String serviceName, 
                                   MarketplaceCallback<ServiceManifest> callback) {
        executor.execute(() -> {
            try {
                String manifestUrl = baseUrl.endsWith("/") 
                    ? baseUrl + "service/" + serviceName + ".json"
                    : baseUrl + "/service/" + serviceName + ".json";
                String json = httpGet(manifestUrl);
                ServiceManifest manifest = ServiceManifest.fromJson(json);
                callback.onSuccess(manifest);
            } catch (Exception e) {
                Log.e(TAG, "Failed to get manifest for: " + serviceName, e);
                callback.onError("Failed to fetch manifest: " + e.getMessage());
            }
        });
    }
    
    /**
     * Install a service from marketplace.
     */
    public void installService(String baseUrl, String serviceName, 
                               MarketplaceCallback<InstalledService> callback) {
        executor.execute(() -> {
            try {
                // Get manifest first
                String manifestUrl = baseUrl.endsWith("/")
                    ? baseUrl + "service/" + serviceName + ".json"
                    : baseUrl + "/service/" + serviceName + ".json";
                String manifestJson = httpGet(manifestUrl);
                ServiceManifest manifest = ServiceManifest.fromJson(manifestJson);
                
                if (manifest == null || manifest.getName() == null) {
                    callback.onError("Invalid manifest");
                    return;
                }
                
                // Create service directory
                File serviceDir = new File(servicesDir, manifest.getName());
                if (!serviceDir.exists()) {
                    serviceDir.mkdirs();
                }
                
                // Download all endpoint files
                InstalledService service = new InstalledService(manifest, baseUrl);
                List<ServiceManifest.Endpoint> endpoints = manifest.getEndpoints();
                
                if (endpoints != null) {
                    for (ServiceManifest.Endpoint ep : endpoints) {
                        String fileUrl = baseUrl.endsWith("/")
                            ? baseUrl + "service/" + serviceName + "/" + ep.getFile()
                            : baseUrl + "/service/" + serviceName + "/" + ep.getFile();
                        String content = httpGet(fileUrl);
                        
                        // Save to disk
                        File file = new File(serviceDir, ep.getFile());
                        writeFile(file, content);
                        
                        // Cache in memory
                        service.getFiles().put(ep.getFile(), content);
                    }
                }
                
                // Save manifest to disk
                File manifestFile = new File(serviceDir, "manifest.json");
                writeFile(manifestFile, manifestJson);
                
                // Add to installed services
                installedServices.put(manifest.getName(), service);
                saveInstalledServices();
                
                Log.i(TAG, "Installed service: " + manifest.getName());
                callback.onSuccess(service);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to install service: " + serviceName, e);
                callback.onError("Failed to install: " + e.getMessage());
            }
        });
    }
    
    private void writeFile(File file, String content) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
        writer.write(content);
        writer.close();
    }
    
    /**
     * Uninstall a service.
     */
    public boolean uninstallService(String serviceName) {
        InstalledService service = installedServices.get(serviceName);
        if (service == null) {
            return false;
        }
        
        // Don't allow uninstalling built-in services
        if ("builtin".equals(service.getSource())) {
            Log.w(TAG, "Cannot uninstall built-in service: " + serviceName);
            return false;
        }
        
        // Delete service directory
        File serviceDir = new File(servicesDir, serviceName);
        if (serviceDir.exists()) {
            deleteDirectory(serviceDir);
        }
        
        // Remove from cache
        installedServices.remove(serviceName);
        saveInstalledServices();
        
        Log.i(TAG, "Uninstalled service: " + serviceName);
        return true;
    }
    
    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }
    
    /**
     * Enable a service (attach endpoints).
     */
    public boolean enableService(String serviceName) {
        InstalledService service = installedServices.get(serviceName);
        if (service == null) {
            return false;
        }
        
        service.setEnabled(true);
        saveInstalledServices();
        Log.i(TAG, "Enabled service: " + serviceName);
        return true;
    }
    
    /**
     * Disable a service (detach endpoints, close instances).
     */
    public boolean disableService(String serviceName) {
        InstalledService service = installedServices.get(serviceName);
        if (service == null) {
            return false;
        }
        
        service.setEnabled(false);
        service.setRunning(false);
        saveInstalledServices();
        Log.i(TAG, "Disabled service: " + serviceName);
        return true;
    }
    
    /**
     * Get all installed services.
     */
    public Map<String, InstalledService> getInstalledServices() {
        return installedServices;
    }
    
    /**
     * Get installed service by name.
     */
    public InstalledService getInstalledService(String name) {
        return installedServices.get(name);
    }
    
    /**
     * Check if a service is installed.
     */
    public boolean isInstalled(String name) {
        return installedServices.containsKey(name);
    }
    
    /**
     * HTTP GET request.
     */
    private String httpGet(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        
        try {
            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + responseCode);
            }
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
    
    /**
     * Shutdown executor.
     */
    public void shutdown() {
        executor.shutdown();
    }
}
