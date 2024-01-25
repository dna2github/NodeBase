import 'package:flutter/services.dart';
import 'dart:convert';
import 'dart:developer';

class NodeBaseApi {
  static const api = MethodChannel('net.seven.nodebase/app');

  static Future<Map<String, dynamic>> apiUtilGetIPs() async {
    /* {
      <interface>: ["<ipv6>", "<ipv4>", ...]
    } */
    try {
      var json = jsonDecode(
          (await api.invokeMethod('util.ip')) as String
      );
      return json;
    } catch (e) {
      log("NodeBase [E] getIPs / ${e.toString()}");
      return {};
    }
  }

  static Future<void> apiUtilMarkExecutable(String filename) async {
    try {
      await api.invokeMethod(
          'util.file.executable',
          <String, dynamic>{"filename": filename}
      );
    } catch (e) {
      log("NodeBase [E] utilMarkExecutable / ${e.toString()}");
    }
  }

  static Future<Map<String, dynamic>> apiAppStatus(String app) async {
    /* {
      state: "none" | "new" | "running" | "dead"
    } */
    try {
      var json = jsonDecode(
          (await api.invokeMethod(
              'app.stat', <String, dynamic>{"name": app}
          )) as String
      );
      return json;
    } catch (e) {
      log("NodeBase [E] appStatus / ${e.toString()}");
      return {};
    }
  }

  static Future<void> apiAppStart(String app, List<String> cmd) async {
    try {
      await api.invokeMethod(
          'app.start', <String, dynamic>{"name": app, "cmd": cmd.join("\x00")}
      );
    } catch (e) {
      log("NodeBase [E] appStart / ${e.toString()}");
    }
  }

  static Future<void> apiAppStop(String app) async {
    try {
      await api.invokeMethod(
          'app.stop', <String, dynamic>{"name": app}
      );
    } catch (e) {
      log("NodeBase [E] appStop / ${e.toString()}");
    }
  }

  static Future<void> apiAppOpenBrowser(String url) async {
    try {
      api.invokeMethod('util.browser.open', <String, dynamic>{
        "url": url,
      });
    } catch (e) {
      log("NodeBase [E] appOpenBrowser / ${e.toString()}");
    }
  }
}