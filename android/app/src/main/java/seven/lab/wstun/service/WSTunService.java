package seven.lab.wstun.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import seven.lab.wstun.R;
import seven.lab.wstun.config.ServerConfig;
import seven.lab.wstun.server.LocalServiceManager;
import seven.lab.wstun.server.NettyServer;
import seven.lab.wstun.server.ServiceManager;
import seven.lab.wstun.ui.MainActivity;

/**
 * Foreground service that runs the HTTP/WebSocket server.
 */
public class WSTunService extends Service {
    
    private static final String TAG = "WSTunService";
    private static final String CHANNEL_ID = "wstun_service";
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_START = "seven.lab.wstun.START";
    public static final String ACTION_STOP = "seven.lab.wstun.STOP";

    private final IBinder binder = new LocalBinder();
    private NettyServer server;
    private ServerConfig config;
    private ServiceManager serviceManager;
    private boolean isRunning = false;

    private ServiceListener listener;

    public interface ServiceListener {
        void onServerStarted();
        void onServerStopped();
        void onServiceChanged();
        void onError(String message);
    }

    public class LocalBinder extends Binder {
        public WSTunService getService() {
            return WSTunService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        config = new ServerConfig(this);
        serviceManager = new ServiceManager();
        serviceManager.setListener(new ServiceManager.ServiceChangeListener() {
            @Override
            public void onServiceAdded(ServiceManager.ServiceEntry service) {
                if (listener != null) {
                    listener.onServiceChanged();
                }
            }

            @Override
            public void onServiceRemoved(ServiceManager.ServiceEntry service) {
                if (listener != null) {
                    listener.onServiceChanged();
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START.equals(action)) {
                startServer();
            } else if (ACTION_STOP.equals(action)) {
                stopServer();
                stopSelf();
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        stopServer();
        super.onDestroy();
    }

    /**
     * Start the HTTP server.
     */
    public void startServer() {
        if (isRunning) {
            Log.w(TAG, "Server already running");
            return;
        }

        try {
            server = new NettyServer(this, config, serviceManager);
            server.start();
            isRunning = true;

            // Start foreground
            startForeground(NOTIFICATION_ID, createNotification());

            if (listener != null) {
                listener.onServerStarted();
            }

            Log.i(TAG, "Server started on port " + config.getPort());
        } catch (Exception e) {
            Log.e(TAG, "Failed to start server", e);
            if (listener != null) {
                listener.onError("Failed to start server: " + e.getMessage());
            }
        }
    }

    /**
     * Stop the HTTP server.
     */
    public void stopServer() {
        if (!isRunning) {
            return;
        }

        if (server != null) {
            server.stop();
            server = null;
        }

        isRunning = false;
        stopForeground(true);

        if (listener != null) {
            listener.onServerStopped();
        }

        Log.i(TAG, "Server stopped");
    }

    /**
     * Check if server is running.
     */
    public boolean isServerRunning() {
        return isRunning;
    }

    /**
     * Get server port.
     */
    public int getPort() {
        return config.getPort();
    }

    /**
     * Check if HTTPS is enabled.
     */
    public boolean isHttpsEnabled() {
        return config.isHttpsEnabled();
    }

    /**
     * Get service manager.
     */
    public ServiceManager getServiceManager() {
        return serviceManager;
    }

    /**
     * Set service listener.
     */
    public void setListener(ServiceListener listener) {
        this.listener = listener;
    }

    /**
     * Kick a service by name.
     */
    public void kickService(String serviceName) {
        if (serviceManager != null) {
            serviceManager.kickService(serviceName);
        }
    }

    /**
     * Get the local service manager.
     */
    public LocalServiceManager getLocalServiceManager() {
        return server != null ? server.getLocalServiceManager() : null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.notification_channel_desc));

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, WSTunService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        );

        String protocol = config.isHttpsEnabled() ? "HTTPS" : "HTTP";
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(protocol + " server running on port " + config.getPort())
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build();
    }
}
