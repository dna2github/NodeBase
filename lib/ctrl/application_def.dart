abstract class IApplicationProcess {
  Future<void> start();
  Future<void> stop();
  Future<String> syncState();
  bool isDead();
  String getName();
  String getPlatform();
  List<String> getCmd();
  Map<String, String> getEnv();
}

abstract class IApplication {
  IApplicationProcess startProcess(String name, String platform, List<String> cmd, Map<String, String> env);
  void stopProcess(String name);
  IApplicationProcess? getApp(String name);
  void dispose();
}