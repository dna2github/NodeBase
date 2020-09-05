import 'package:flutter/services.dart';

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
}
