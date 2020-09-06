import 'package:flutter/services.dart';
import './io.dart';

class NodeBaseApi {
  static final batteryApi = const MethodChannel('net.seven.nodebase/battery');
  static final appApi = const MethodChannel('net.seven.nodebase/app');

  static Future<String> getBatteryLevel() async {
    String batteryLevel;
    try {
      final int lv = await batteryApi.invokeMethod('getBatteryLevel');
      batteryLevel = '${lv}%';
    } on PlatformException catch (e) {
      batteryLevel = 'Failed: ${e.message}';
    }
    return batteryLevel;
  }

  static Future<void> requestExternalStoragePermission () async {
    try { appApi.invokeMethod('RequestExternalStoragePermission'); } catch (e) {}
  }

  static Future<String> fetchExecutable (String url) async {
    if (url == null || url == "") return null;
    final name = url.split("/").last;
    final dst = (await getAppFileReference('/bin/${name}')).path;
    try {
      appApi.invokeMethod('FetchExecutable', <String, dynamic>{
        "url": url,
        "target": dst
      });
      return dst;
    } catch(e) {
      return null;
    }
  }
}
