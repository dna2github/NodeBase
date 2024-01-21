package net.seven.nodebase.nodebase;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

public class ForegroundNodeService extends Service {
    private static final String CHANNEL_ID = "net.seven.nodebase.foregroundservice";
    public static final String ARGV = "NodeService";

    private final Handler appHandler;

    public ForegroundNodeService() {
        this.appHandler = new Handler();
    }

    private void registerNotificationItem() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel srvChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "NodeBase Service",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(srvChannel);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("NodeBase")
                .setContentText("application nodebase service running ...")
                .build();
        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        registerNotificationItem();
        while (intent != null) {
            String[] argv = intent.getStringArrayExtra(ARGV);
            if (argv == null || argv.length < 3) break;
            if (!NodeAppService.getAuthToken().equals(argv[0])) break;
            switch (argv[1]) {
                case "start" -> {
                    if (argv.length < 4) break;
                    String name = argv[2];
                    String cmd = argv[3];
                    NodeAppService.startNodeApp(this.appHandler, name, cmd.split("\0"));
                }
                case "restart" -> {
                    String name = argv[2];
                    NodeAppService.restartNodeApp(this.appHandler, name);
                }
                case "stop" -> {
                    String name = argv[2];
                    NodeAppService.stopNodeApp(name);
                }
            }
            break;
        }
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NodeAppService.refreshAuthToken();
        NodeAppService.stopNodeApps();
    }

    @Override
    public void onDestroy() {
        NodeAppService.stopNodeApps();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}