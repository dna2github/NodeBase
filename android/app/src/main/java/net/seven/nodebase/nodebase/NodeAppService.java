package net.seven.nodebase.nodebase;

import java.util.HashMap;
import java.util.UUID;
import android.os.Handler;

public class NodeAppService {
    // XXX actually it is not safe to store data as static
    //     we just reduce attack possibilities
    private static String AUTH_TOKEN = refreshAuthToken();

    public static String refreshAuthToken() {
        AUTH_TOKEN = UUID.randomUUID().toString();
        return AUTH_TOKEN;
    }

    public static String getAuthToken() {
        return AUTH_TOKEN;
    }

    private static final HashMap<String, NodeAppMonitor> services = new HashMap<>();

    public static int getRunningNodeAppCount() {
        int count = 0;
        for (NodeAppMonitor app : services.values()) {
            if (app != null && !app.nodebaseIsDead()) count ++;
        }
        return count;
    }

    public static void startNodeApp(Handler handler, String name, String[] cmd) {
        synchronized (services) {
            NodeAppMonitor app;
            if (services.containsKey(name)) {
                app = services.get(name);
                if (app != null) app.nodebaseStop();
            }
            app = new NodeAppMonitor(name, cmd);
            app.nodebaseStart(handler);
            services.put(name, app);
        }
    }

    public static void restartNodeApp(Handler handler, String name) {
        synchronized (services) {
            if (!services.containsKey(name)) return;
            NodeAppMonitor app = services.get(name);
            if (app == null) {
                services.remove(name);
                return;
            }
            services.put(name, app.nodebaseRestart(handler));
        }
    }

    public static void stopNodeApp(String name) {
        synchronized (services) {
            if (!services.containsKey(name)) return;
            NodeAppMonitor app = services.get(name);
            services.remove(name);
            if (app != null) app.nodebaseStop();
        }
    }

    public static void stopNodeApps() {
        for (String name : services.keySet()) {
            stopNodeApp(name);
        }
    }

    public static NodeAppMonitor getNodeApp(String name) {
        if (!services.containsKey(name)) return null;
        return services.get(name);
    }
}
