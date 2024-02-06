// manage application runtime

import 'dart:async';

import '../util/api.dart';

class ApplicationProcess {
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

  Future<void> start() async {
    await NodeBaseApi.apiAppStart(name, cmd, env: env);
  }

  Future<void> stop() async {
    await NodeBaseApi.apiAppStop(name);
  }

  Future<String> syncState() async {
    final obj = await NodeBaseApi.apiAppStatus(name);
    final r = obj["state"];
    state = r;
    return r;
  }

  bool isDead() => state == "dead";
}

class Application {
  late StreamSubscription listener;

  Application({
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

  void startProcess(String name, List<String> cmd, Map<String, String> env) {
    final app = ApplicationProcess(name: name, cmd: cmd, env: env);
    // XXX: if we want sandbox to run applications,
    //      on windows, MacOS, maybe consider cygwin, winehq, WSL and docker impl
    //      on Linux, Android, consider proot
    app.start();
  }

  void stopProcess(String name) {
    final app = runtime[name];
    if (app == null) return;
    app.stop();
  }

  ApplicationProcess? getApp(String name) => runtime[name];
  List<ApplicationProcess> getRunningApp() {
    final List<ApplicationProcess> r = [];
    runtime.forEach((name, app) {
      if (!app.isDead()) r.add(app);
    });
    return r;
  }

  void dispose() {
    listener.cancel();
    runtime.forEach((_, app) => app.stop());
  }

  String baseDir;
  Map<String, ApplicationProcess> runtime = {};
}