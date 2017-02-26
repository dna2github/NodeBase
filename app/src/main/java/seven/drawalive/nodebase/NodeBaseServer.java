package seven.drawalive.nodebase;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;


public class NodeBaseServer extends Service {

   public NodeBaseServer() {
      _valid = false;
      _crash = true;
      _workdir = null;
      _appdir = null;
      _appentry = null;
      _node = null;
   }

   @Override
   public IBinder onBind(Intent intent) {
      throw new UnsupportedOperationException("Not yet implemented");
   }

   @Override
   public int onStartCommand(Intent intent, int flags, int startId) {
      authenticate(intent);
      if (intent != null) {
         String[] args = intent.getStringArrayExtra("signal");
         if (args.length > 0) {
            switch (args[0]) {
               case "start":
                  Log.i("Server:Command",
                        String.format("Server starts for \"%s\" at \"%s\"", args[2], args[1]));
                  startNode(args[1], args[2], args[3]);
                  break;
               case "restart":
                  Log.i("Server:Command",
                        String.format("Server restarts for \"%s\" at \"%s\"", _appentry, _appdir));
                  restartNode();
                  break;
               case "stop":
                  Log.i("Server:Command",
                        String.format("Server stops for \"%s\" at \"%s\"", _appentry, _appdir));
                  stopNode();
                  break;
            }
         }
      }
      // running until explicitly stop
      return START_STICKY;
   }

   @Override
   public void onCreate() {
      checkNodeBinaryHash();
      prepareEnvironment();
   }

   @Override
   public void onDestroy() {
      stopNode();
   }

   private void checkNodeBinaryHash() {
      _valid = true;
   }

   private void authenticate(Intent intent) {
      String auth = Utils.getServiceAuthentication();
      if (auth != "NodeBase") {
         _valid = false;
         return;
      }
      Utils.setServiceAuthentication(null);
      _valid = true;
   }

   private void prepareEnvironment() {
      if (!_valid) return;

      _workdir = this.getApplicationInfo().dataDir;
      Utils.resetNodeJS(this, _workdir);
   }

   public int startNode(String appdir, String appentry, String appparams) {
      if (!_valid) return -1;
      int result = 0;
      _crash = true;
      _appdir = appdir;
      _appentry = appentry;
      _appparams = appparams;
      Utils.setServiceInfo(new String[] {_appdir, _appentry});

      if (_node != null) {
         // node is running
         return result;
      }

      // e.g. cd "/sdcard/.nodebase/apps/test" &&
      //      /data/seven.drawalive.nodebase/node/node "index.js"
      String[] cmd;
      if (_appparams != null && _appparams.length() > 0) {
         cmd = new String[] {
               String.format("%s/node/node", _workdir),
               String.format("%s/%s", _appdir, _appentry),
               _appparams
         };
      } else {
         cmd = new String[] {
               String.format("%s/node/node", _workdir),
               String.format("%s/%s", _appdir, _appentry)
         };
      }
      Log.i("Server:Start", String.format("Command - %s", cmd));
      try {
         _node = new NodeMonitor(this, Runtime.getRuntime().exec(cmd));
         _node.start();
      } catch (Exception e) {
         Log.e("Server:Start", "Cannot start \"node\"", e);
         return -2;
      }

      return result;
   }

   public int restartNode() {
      if (!_valid) return -1;
      int result = 0;
      stopNode();
      startNode(_appdir, _appentry, _appparams);
      return result;
   }

   public int stopNode() {
      if (!_valid) return -1;
      int result = 0;
      if (_node != null) {
         _crash = false;
         _node.kill();
         _node = null;
      }
      Utils.setServiceInfo(null);
      return result;
   }

   public void onNodeTerminated() {
      _node = null;
      if (_crash) {
         // do something to deal with node crash
         Utils.setServiceInfo(null);
      }
   }

   private boolean _valid, _crash;
   private String _workdir, _appdir, _appentry, _appparams;
   private NodeMonitor _node;

}
