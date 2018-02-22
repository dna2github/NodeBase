package seven.drawalive.nodebase;

import android.util.Log;

import java.io.IOException;

public class NodeMonitor extends Thread {
   public enum STATE { BORN, READY, RUNNING, DEAD };

   public NodeMonitor(String service_name, String[] command) {
      state = STATE.BORN;
      this.service_name = service_name;
      this.command = command;
      event = null;
   }

   public NodeMonitor setEvent(NodeMonitorEvent event) {
      this.event = event;
      return this;
   }

   @Override
   public void run() {
      try {
         state = STATE.READY;
         if (event != null) event.before(command);
         Log.i("NodeService:NodeMonitor", String.format("node process starting - %s", command));
         node_process = Runtime.getRuntime().exec(command);
         state = STATE.RUNNING;
         if (event != null) event.started(command, node_process);
         Log.i("NodeService:NodeMonitor", "node process running ...");
         node_process.waitFor();
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
      } catch (IOException e) {
         Log.e("NodeService:NodeMonitor", "node process error", e);
         node_process = null;
         if (event != null) event.error(command, null);
      } catch (InterruptedException e) {
         Log.e("NodeService:NodeMonitor", "node process error", e);
         if (event != null) event.error(command, node_process);
      } finally {
         state = STATE.DEAD;
         if (event != null) event.after(command, node_process);
         Log.i("NodeService:NodeMonitor", "node process stopped ...");
      }
   }

   public String getServiceName() {
      return service_name;
   }

   public String[] getCommand() {
      return command;
   }

   public boolean stopService() {
      if (state == STATE.RUNNING) node_process.destroy();
      return true;
   }

   public NodeMonitor restartService() {
      stopService();
      NodeMonitor m = new NodeMonitor(service_name, command);
      if (event != null) m.setEvent(event);
      return m;
   }

   public boolean isRunning() {
      return state == STATE.RUNNING;
   }

   public boolean isReady() {
      return state == STATE.READY;
   }

   public boolean isDead() {
      return state == STATE.DEAD;
   }

   private STATE state;
   private String service_name;
   private Process node_process;
   private String[] command;
   private NodeMonitorEvent event;
}