import 'dart:async';

import 'package:flutter/services.dart';
import 'dart:developer';

class NodeBaseApi {
  static const api = MethodChannel('net.seven.nodebase/app');
  static const event = EventChannel('net.seven.nodebase/event');

  static Future<Map<String, dynamic>> apiUtilGetIPs() async {
    /* {
      <interface>: ["<ipv6>", "<ipv4>", ...]
    } */
    try {
      var json = await api.invokeMethod('util.ip');
      Map<String, dynamic> r = {};
      json.forEach((key, value) {
        r[key.toString()] = value;
      });
      return Future.value(r);
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

  static Future<String> apiUtilGetArch() async {
    try {
      return await api.invokeMethod("util.arch");
    } catch(e) {
      log("NodeBase [E] utilGetArch / ${e.toString()}");
      return "";
    }
  }

  static Future<String> apiUtilGetWorkspacePath() async {
    try {
      return await api.invokeMethod("util.workspace");
    } catch(e) {
      log("NodeBase [E] utilGetWorkspacePath / ${e.toString()}");
      return "";
    }
  }

  static Future<Map<String, dynamic>> apiAppStatus(String app) async {
    /* {
      state: "none" | "new" | "running" | "dead"
    } */
    try {
      var json = await api.invokeMethod('app.stat', <String, dynamic>{"name": app});
      Map<String, dynamic> r = {};
      json.forEach((key, value) {
        r[key.toString()] = value;
      });
      return r;
    } catch (e) {
      log("NodeBase [E] appStatus / ${e.toString()}");
      return {};
    }
  }

  static Future<void> apiAppStart(
      String app,
      List<String> cmd,
      { Map<String, String> env = const {} }
  ) async {
    try {
      await api.invokeMethod(
          'app.start', <String, dynamic>{"name": app, "cmd": cmd, "env": env}
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