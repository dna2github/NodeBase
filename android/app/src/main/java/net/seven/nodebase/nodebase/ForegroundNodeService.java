package net.seven.nodebase.nodebase;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

public class ForegroundNodeService extends Service {
    private static final String CHANNEL_ID = "net.seven.nodebase.foregroundservice";
    public static final String ARGV = "NodeService";

    private Thread watchDog;

    public ForegroundNodeService() { }

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

        watchDog = new Thread(new Runnable() {
            @Override
            public void run() {
                Intent it = new Intent(
                        ForegroundNodeService.this.getApplicationContext(),
                        ForegroundNodeService.class
                );
                while(true) {
                    int count = NodeAppService.getRunningNodeAppCount();
                    if (count <= 0) break;
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        break;
                    }
                };
                ForegroundNodeService.this.stopService(it);
            }
        });
        watchDog.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        registerNotificationItem();
        return Service.START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
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