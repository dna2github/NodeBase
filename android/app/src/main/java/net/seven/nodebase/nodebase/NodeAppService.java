package net.seven.nodebase.nodebase;

import java.util.HashMap;

public class NodeAppService {
    private static final NodeBaseEventHandler eventHandler = new NodeBaseEventHandler();
    private static final HashMap<String, NodeAppMonitor> services = new HashMap<>();

    public static NodeBaseEventHandler getEventHandler() {
        return eventHandler;
    }

    public static int getRunningNodeAppCount() {
        int count = 0;
        for (NodeAppMonitor app : services.values()) {
            if (app != null && !app.nodebaseIsDead()) count ++;
        }
        return count;
    }

    public static void startNodeApp(String name, String[] cmd, HashMap<String, String> env) {
        synchronized (services) {
            NodeAppMonitor app;
            if (services.containsKey(name)) {
                app = services.get(name);
                if (app != null && !app.nodebaseIsDead()) return;
                // only process if the service is null/dead
            }
            app = new NodeAppMonitor(name, cmd, env);
            app.nodebaseStart();
            services.put(name, app);
        }
    }

    public static void restartNodeApp(String name) {
        synchronized (services) {
            if (!services.containsKey(name)) return;
            NodeAppMonitor app = services.get(name);
            if (app == null) {
                services.remove(name);
                return;
            }
            services.put(name, app.nodebaseRestart());
        }
    }

    public static void stopNodeApp(String name) {
        synchronized (services) {
            if (!services.containsKey(name)) return;
            NodeAppMonitor app = services.get(name);
            if (app == null) return;
            if (app.nodebaseIsDead()) return;
            app.nodebaseStop();
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
