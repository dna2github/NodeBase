package seven.drawalive.nodebase;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class NodeMonitor extends Thread {
   public NodeMonitor(NodeBaseServer server, Process process) {
      _running = true;
      _server = server;
      _process = process;
   }

   public void kill() {
      if (_running) {
         _process.destroy();
      }
   }

   @Override
   public void run() {
      try {
         Log.i("NodeMonitor", "node process running ...");
         _process.waitFor();

         /*
         BufferedReader reader = new BufferedReader(
               new InputStreamReader(_process.getInputStream()));
         String line = null;
         while ((line = reader.readLine()) != null) {
            Log.d("NodeMonitor", line);
         }
         Log.d("-----", "==========================");
         reader = new BufferedReader(
               new InputStreamReader(_process.getErrorStream()));
         while ((line = reader.readLine()) != null) {
            Log.d("NodeMonitor", line);
         }
         */
      } catch (Exception e) {
         // probably interrupted
         Log.e("NodeMonitor", "node error", e);
      }
      Log.i("NodeMonitor", "node process stopped ...");
      _running = false;
      _server.onNodeTerminated();
   }

   private boolean _running;
   private NodeBaseServer _server;
   private Process _process;
}
