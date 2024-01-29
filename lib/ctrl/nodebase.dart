import '../ctrl/application.dart';
import '../ctrl/config.dart';
import '../ctrl/platform.dart';
import '../util/api.dart';
import '../util/event.dart' as event;

class NodeBaseController {
  late Application application;
  late Platform platform;
  late Etc config;

  Future<void> initializeApp() async {
    final parts = (await NodeBaseApi.apiUtilGetArch()).split("|")[0].split("-");
    String os = parts[0];
    String arch = parts[1];
    config = Etc();
    platform = Platform(os: os, arch: arch);
    application = Application();
    event.initializeToken.add(true);
  }
}

final instance = NodeBaseController();