// manage application runtime

import 'dart:async';

import './application_def.dart';

import '../util/api.dart';

class ApplicationProcess implements IApplicationProcess {
  ApplicationProcess({
    required this.name,
    required this.cmd,
    this.platform = "-",
    this.env = const {},
  });

  String name;
  List<String> cmd;
  Map<String, String> env;
  String platform;
  String state = "new";

  @override
  String getName() => name;
  @override
  String getPlatform() => platform;
  @override
  List<String> getCmd() => cmd;
  @override
  Map<String, String> getEnv() => env;

  @override
  Future<void> start() async {
    await NodeBaseApi.apiAppStart(name, cmd, env: env);
  }

  @override
  Future<void> stop() async {
    await NodeBaseApi.apiAppStop(name);
  }

  @override
  Future<String> syncState() async {
    final obj = await NodeBaseApi.apiAppStatus(name);
    final r = obj["state"];
    state = r;
    return r;
  }

  @override
  bool isDead() => state == "dead";
}

class ApplicationLocal implements IApplication {
  late StreamSubscription listener;

  ApplicationLocal({
    required this.baseDir
  }) {
    _connect();
  }

  void _connect() {
    listener = NodeBaseApi.event.receiveBroadcastStream("app").listen((message) {
      if (message is List) {
        final name = message[0];
        final state = message[1];
        final app = runtime[name];
        if (app != null) {
          app.state = state;
          if (state == "dead") runtime.remove(name);
        }
      }
    });
  }

  @override
  ApplicationProcess startProcess(
      String name,
      String platform,
      List<String> cmd,
      Map<String, String> env
  ) {
    final app = ApplicationProcess(name: name, cmd: cmd, env: env, platform: platform);
    // XXX: if we want sandbox to run applications,
    //      on windows, MacOS, maybe consider cygwin, winehq, WSL and docker impl
    //      on Linux, Android, consider proot
    app.start();
    runtime[name] = app;
    return app;
  }

  @override
  void stopProcess(String name) {
    final app = runtime[name];
    if (app == null) return;
    runtime.remove(name);
    app.stop();
  }

  @override
  ApplicationProcess? getApp(String name) => runtime[name];
  List<ApplicationProcess> getRunningApp() {
    final List<ApplicationProcess> r = [];
    runtime.forEach((name, app) {
      if (!app.isDead()) r.add(app);
    });
    return r;
  }

  @override
  void dispose() {
    listener.cancel();
    runtime.forEach((_, app) => app.stop());
  }

  String baseDir;
  Map<String, ApplicationProcess> runtime = {};
}