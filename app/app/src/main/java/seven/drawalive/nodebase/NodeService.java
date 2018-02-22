package seven.drawalive.nodebase;


import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.UUID;

public class NodeService extends Service {
   public static final String ARGV = "NodeService";
   public static final HashMap<String, NodeMonitor> services = new HashMap<>();
   public static String AUTH_TOKEN = refreshAuthToken();

   public static String refreshAuthToken() {
      UUID uuid = UUID.randomUUID();
      return uuid.toString();
   }

   public static String checkOutput(String[] cmd) {
      try {
         Process p = Runtime.getRuntime().exec(cmd);
         p.waitFor();
         InputStream is = p.getInputStream();
         int len = is.available();
         byte[] b = null;
         if (len > 0) {
            b = new byte[len];
            len = is.read(b);
         }
         is.close();
         if (b == null) {
            return null;
         }
         return new String(b, 0, len);
      } catch (Exception e) {
         return null;
      }
   }


   public static void touchService(Context context, String[] args) {
      Log.i("NodeService:Signal", "Start Service");
      Log.i("NodeService:Signal", String.format("Command - %s", args[1]));
      Intent intent = new Intent(context, NodeService.class);
      intent.putExtra(NodeService.ARGV, args);
      context.startService(intent);
   }

   public static void stopService(Context context) {
      Log.i("NodeService:Signal", "Stop Service");
      Intent intent = new Intent(context, NodeService.class);
      context.stopService(intent);
   }

   @Nullable
   @Override
   public IBinder onBind(Intent intent) {
      throw new UnsupportedOperationException("Not yet implemented");
   }

   @Override
   public int onStartCommand(Intent intent, int flags, int startId) {
      while (intent != null) {
         String[] argv = intent.getStringArrayExtra(ARGV);
         if (argv.length < 3) break;
         String auth_token = argv[0];
         String cmd = argv[1];
         String first = argv[2];
         if (AUTH_TOKEN.compareTo(auth_token) != 0) break;
         /*
           command:
             - start
                - start <name> <cmd>
                  - <cmd> = <node> <main.js> ...<argv>
             - restart
                - restart <name> -> restart node app by name
             - stop
                - stop !         -> stop all node apps
                - stop <name>    -> stop node app by name
          */
         switch(cmd) {
            case "start":
               if (argv.length < 4) break;
               cmd = argv[3];
               startNodeApp(first /* name */, cmd);
               break;
            case "restart":
               restartNodeApp(first /* name */);
               break;
            case "stop":
               if (first == "!") {
                  stopNodeApps();
               } else {
                  stopNodeApp(first /* name */);
               }
               break;
         }
         break;
      }
      // running until explicitly stop
      return START_STICKY;
   }

   @Override
   public void onCreate() {
      NodeService.refreshAuthToken();
      stopNodeApps();
   }

   @Override
   public void onDestroy() {
      stopNodeApps();
   }

   private void stopNodeApps() {
      int n = services.keySet().size();
      String[] keys = new String[n];
      services.keySet().toArray(keys);
      for(String name : keys) {
         stopNodeApp(name);
      }
   }

   private void stopNodeApp(String name) {
      if (!services.containsKey(name)) return;
      NodeMonitor monitor = services.get(name);
      monitor.stopService();
      services.remove(name);
   }

   private void restartNodeApp(String name) {
      if (!services.containsKey(name)) return;
      NodeMonitor monitor = services.get(name);
      stopNodeApp(name);
      monitor = monitor.restartService();
      services.put(name, monitor);
      monitor.start();
   }

   private void startNodeApp(String name, String cmd) {
      stopNodeApp(name);
      Log.d("NodeService:Command", String.format("%s", cmd));
      String[] exec = StringUtils.parseArgv(cmd);
      NodeMonitor monitor = new NodeMonitor(name, exec);
      services.put(name, monitor);
      monitor.start();
   }
}
