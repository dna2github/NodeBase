// manage application runtime

import 'dart:async';

import '../util/api.dart';

class ApplicationProcess {
  ApplicationProcess({
    required this.name,
    required this.cmd,
  });

  String name;
  List<String> cmd;
  Map<String, String> env = {};
  String state = "new";

  void start() {
    NodeBaseApi.apiAppStart(name, cmd, env: env);
  }

  void stop() {
    NodeBaseApi.apiAppStop(name);
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
  static final Finalizer<Application> _finalizer =
    Finalizer((Application instance) {
      instance.listener.cancel();
      instance.runtime.forEach((_, app) => app.stop());
    });

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
    _finalizer.attach(this, this, detach: this);
  }

  void startProcess(String name, List<String> cmd, Map<String, String> env) {
    final app = ApplicationProcess(name: name, cmd: cmd);
    app.env = env;
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

  String baseDir;
  Map<String, ApplicationProcess> runtime = {};
}