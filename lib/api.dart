import 'package:flutter/services.dart';
import './io.dart';

class NodeBaseApi {
  static final batteryApi = const MethodChannel('net.seven.nodebase/battery');
  static final appApi = const MethodChannel('net.seven.nodebase/app');
  static final nodebaseApi = const MethodChannel('net.seven.nodebase/nodebase');

  static final eventApi = const EventChannel('net.seven.nodebase/event');

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

  static Future<void> requestExternalStoragePermission() async {
    try {
      appApi.invokeMethod('RequestExternalStoragePermission');
    } catch (e) {}
  }

  static Future<String> fetchExecutable(String url) async {
    if (url == "") return "";
    final name = url.split("/").last;
    final dst = (await getAppFileReference('/bin/${name}')).path;
    // e.g. node -> /path/to/app/bin/node
    if (url.indexOf("://") < 0) return dst;
    // e.g. file://.../node http://.../node https://.../node -> /path/to/app/bin/node
    try {
      appApi.invokeMethod(
          'FetchExecutable', <String, dynamic>{"url": url, "target": dst});
      if (dst.endsWith(".zip")) return dst.substring(0, dst.length - 4);
      return dst;
    } catch (e) {
      return "";
    }
  }

  static Future<String> fetchApp(String url) async {
    if (url == "") return "";
    var name = url.split("/").last;
    if (name.endsWith(".zip")) name = name.substring(0, name.length - 4);
    final dst = (await getAppFileReference('/apps/${name}')).path;
    // e.g. node -> /path/to/app/apps/node
    if (url.indexOf("://") < 0) return dst;
    try {
      appApi.invokeMethod(
          'FetchApp', <String, dynamic>{"url": url, "target": dst});
      return dst;
    } catch (e) {
      return "";
    }
  }

  static Future<String?> fetchWifiIpv4() async {
    try {
      return appApi.invokeMethod('FetchWifiIpv4');
    } catch (e) {
      return "0.0.0.0";
    }
  }

  static Future<String?> appStatus(String app) async {
    try {
      return nodebaseApi
          .invokeMethod('GetStatus', <String, dynamic>{"app": app});
    } catch (e) {
      return "error";
    }
  }

  static Future<void> appStart(String app, String cmd) async {
    try {
      nodebaseApi
          .invokeMethod('Start', <String, dynamic>{"app": app, "cmd": cmd});
    } catch (e) {}
  }

  static Future<void> appStop(String app) async {
    try {
      nodebaseApi.invokeMethod('Stop', <String, dynamic>{"app": app});
    } catch (e) {}
  }

  static Future<void> appUnpack(String app, String zipfile) async {
    final appBaseDir = await ioGetAppBaseDir(app);
    try {
      nodebaseApi.invokeMethod('Unpack', <String, dynamic>{
        "app": app,
        "path": appBaseDir,
        "zipfile": zipfile
      });
    } catch (e) {}
  }

  static Future<void> appPack(String app, String zipfile) async {
    final appBaseDir = await ioGetAppBaseDir(app);
    try {
      nodebaseApi.invokeMethod('Pack', <String, dynamic>{
        "app": app,
        "path": appBaseDir,
        "zipfile": zipfile
      });
    } catch (e) {}
  }

  static Future<void> appBrowser(String url) async {
    try {
      nodebaseApi.invokeMethod('Browser', <String, dynamic>{
        "url": url,
      });
    } catch (e) {}
  }
}
