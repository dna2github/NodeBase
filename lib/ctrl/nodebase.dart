import 'dart:developer';
import 'dart:io';



import '../ctrl/application_def.dart';
import '../ctrl/application_local.dart';
import '../ctrl/platform_def.dart';
import '../ctrl/platform_local.dart';
import '../util/fs.dart';
import '../util/api.dart';
import '../util/event.dart' as event;

const defaultPlatformBaseUrl = "https://raw.githubusercontent.com/wiki/dna2github/NodeBase/market/v1";
//const defaultPlatformBaseUrl = "http://127.0.0.1:8000";

class NodeBaseController {
  late IApplication application;
  late IPlatform platform;
  bool isSupported = false;

  Future<void> initializeApp() async {
    final parts = (await NodeBaseApi.apiUtilGetArch()).split("|")[0].split("-");
    String os = parts[0];
    String arch = parts[1];
    final appBaseDir = await fsGetBaseDir();
    var baseUrl = defaultPlatformBaseUrl;
    // TODO: read app config and get default base url
    platform = PlatformLocal(baseUrl: baseUrl, baseDir: appBaseDir, os: os, arch: arch);
    application = ApplicationLocal(baseDir: appBaseDir);
    try {
      // TODO: read user settings, app list, platform list
      //       get latest app, platform list from remote
      final download = platform.downloadNodeBaseJson();
      if (!File(platform.getNodeBaseJsonFilename()).existsSync()) {
        await download;
      }
    } catch (e) {
      log("NodeBase [E] cannot download nodebase.json");
    }
    isSupported = await platform.isSupported();
    // TODO: check platform version; if no change, skip download list
    try {
      final download = platform.downloadApplicationListJson();
      if (!File(platform.getApplicationListJsonFilename()).existsSync()) {
        await download;
      }
    } catch(e) {
      log("NodeBase [E] cannot download app-list.json");
    }

    try {
      final download = platform.downloadPlatformListJson();
      if (!File(platform.getPlatformListJsonFilename()).existsSync()) {
        await download;
      }
    } catch(e) {
      log("NodeBase [E] cannot download plm-list.json");
    }

    event.initializeToken.add(true);
  }
}

final instance = NodeBaseController();