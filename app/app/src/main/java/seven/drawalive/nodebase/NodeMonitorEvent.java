package seven.drawalive.nodebase;

public interface NodeMonitorEvent {
   void before(String[] cmd);
   void started(String[] cmd, Process process);
   void error(String[] cmd, Process process);
   void after(String[] cmd, Process process);
}
