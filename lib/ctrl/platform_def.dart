import 'dart:async';

abstract class IPlatform {
  void changeBaseUrl(String url);
  Future<bool> isSupported();
  String getName();
  String getNodeBaseJsonFilename();
  String getApplicationListJsonFilename();
  String getPlatformListJsonFilename();
  Future<String> getApplicationBaseDir(String name, String version);
  Future<String> getPlatformBaseDir(String name, String version);
  Future<Map<String, dynamic>> readNodeBaseJson();
  Future<Map<String, dynamic>> readApplicationListJson();
  Future<Map<String, dynamic>> readPlatformListJson();
  Future<Map<String, dynamic>> readApplicationMetaJson(String name, String version);
  Future<Map<String, dynamic>> readPlatformMetaJson(String name, String version);
  Future<void> downloadNodeBaseJson({StreamController? cancel});
  Future<void> downloadApplicationListJson({StreamController? cancel});
  Future<void> downloadPlatformListJson({StreamController? cancel});
  Future<void> downloadApplicationMetaJson(String name, String version, {StreamController? cancel});
  Future<void> downloadPlatformMetaJson(String name, String version, {StreamController? cancel});
  Future<void> downloadApplicationBinary(String name, String version, String platform, String url, {StreamController? cancel});
  Future<void> downloadPlatformBinary(String name, String version, String url, {StreamController? cancel});
  Future<void> downloadCancel(String targetFilename);
  Future<Map<String, List<String>>> listAvailableApplicationList();
  Future<Map<String, List<String>>> listInstalledApplicationList();
  Future<Map<String, dynamic>> readApplicationConfig(String name, String version);
  Future<void> writeApplicationConfig(String name, String version, Map<String, dynamic> json);
  Future<void> removeApplicationBinary(String name, String version, String platform);
  Future<Map<String, List<String>>> listAvailablePlatformList();
  Future<Map<String, List<String>>> listInstalledPlatformList();
  Future<List<String>> listPlatformExecutableList(String name, String version);
  Future<Map<String, dynamic>> readPlatformConfig(String name, String version);
  Future<void> writePlatformConfig(String name, String version, Map<String, dynamic> json);
  Future<void> removePlatformBinary(String name, String version);
}