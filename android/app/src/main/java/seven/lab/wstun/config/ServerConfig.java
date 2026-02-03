package seven.lab.wstun.config;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Server configuration stored in SharedPreferences.
 */
public class ServerConfig {
    
    private static final String PREFS_NAME = "wstun_config";
    private static final String KEY_PORT = "port";
    private static final String KEY_HTTPS_ENABLED = "https_enabled";
    private static final String KEY_CERT_GENERATED = "cert_generated";
    private static final String KEY_CORS_ORIGINS = "cors_origins";
    private static final String KEY_FILESHARE_ENABLED = "fileshare_enabled";
    private static final String KEY_CHAT_ENABLED = "chat_enabled";

    private final SharedPreferences prefs;

    public ServerConfig(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public int getPort() {
        return prefs.getInt(KEY_PORT, 8080);
    }

    public void setPort(int port) {
        prefs.edit().putInt(KEY_PORT, port).apply();
    }

    public boolean isHttpsEnabled() {
        return prefs.getBoolean(KEY_HTTPS_ENABLED, false);
    }

    public void setHttpsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HTTPS_ENABLED, enabled).apply();
    }

    public boolean isCertGenerated() {
        return prefs.getBoolean(KEY_CERT_GENERATED, false);
    }

    public void setCertGenerated(boolean generated) {
        prefs.edit().putBoolean(KEY_CERT_GENERATED, generated).apply();
    }

    /**
     * Get CORS allowed origins. Default is "*" (all origins).
     * Can be comma-separated list of origins or "*".
     */
    public String getCorsOrigins() {
        return prefs.getString(KEY_CORS_ORIGINS, "*");
    }

    /**
     * Set CORS allowed origins.
     * Use "*" to allow all origins, or comma-separated list like:
     * "http://localhost:3000,http://192.168.1.100:8080"
     */
    public void setCorsOrigins(String origins) {
        prefs.edit().putString(KEY_CORS_ORIGINS, origins).apply();
    }

    /**
     * Check if FileShare service is enabled.
     */
    public boolean isFileshareEnabled() {
        return prefs.getBoolean(KEY_FILESHARE_ENABLED, false);
    }

    /**
     * Enable or disable FileShare service.
     */
    public void setFileshareEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_FILESHARE_ENABLED, enabled).apply();
    }

    /**
     * Check if Chat service is enabled.
     */
    public boolean isChatEnabled() {
        return prefs.getBoolean(KEY_CHAT_ENABLED, false);
    }

    /**
     * Enable or disable Chat service.
     */
    public void setChatEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_CHAT_ENABLED, enabled).apply();
    }
}
