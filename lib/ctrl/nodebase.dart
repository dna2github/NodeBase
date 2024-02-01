import '../ctrl/application.dart';
import '../ctrl/platform.dart';
import '../util/fs.dart';
import '../util/api.dart';
import '../util/event.dart' as event;

const defaultPlatformBaseUrl = "https://raw.githubusercontent.com/wiki/dna2github/NodeBase/market/v1";

class NodeBaseController {
  late Application application;
  late Platform platform;

  Future<void> initializeApp() async {
    final parts = (await NodeBaseApi.apiUtilGetArch()).split("|")[0].split("-");
    String os = parts[0];
    String arch = parts[1];
    final appBaseDir = await fsGetBaseDir();
    var baseUrl = defaultPlatformBaseUrl;
    // TODO: read app config and get default base url
    platform = Platform(baseUrl: baseUrl, baseDir: appBaseDir, os: os, arch: arch);
    application = Application(baseDir: appBaseDir);
    // TODO: read user settings, app list, platform list
    //       get latest app, platform list from remote
    event.initializeToken.add(true);
  }
}

final instance = NodeBaseController();