import 'dart:async';

import 'package:flutter/material.dart';
import '../util/api.dart';
import '../util/event.dart' as event;
import '../ctrl/nodebase.dart' as nodebase;
import '../comp/app_runtime_tile.dart';
import '../comp/download_tile.dart';
import '../comp/app_tile.dart';
import '../comp/platform_tile.dart';

class PrimaryPage extends StatefulWidget {
  const PrimaryPage({super.key});

  @override
  State<StatefulWidget> createState() => _PrimaryPageState();
}

class _PrimaryPageState extends State<PrimaryPage> {
  List<AppRuntimeTile> runtimeList = [];
  List<DownloadTile> downloadList = [];
  List<AppTile> appList = [];
  List<PlatformTile> plmList = [];

  late StreamSubscription downloadProgress;
  late StreamSubscription applicationEvent;

  updateDependencyState() {
    Map<String, int> appRef = {};
    Map<String, int> plmRef = {};
    for (final one in appList) {
      appRef["${one.name}-${one.version}"] = 0;
    }
    for (final one in plmList) {
      plmRef["${one.name}-${one.version}"] = 0;
    }
    for (final one in runtimeList) {
      appRef[one.process.name] = (appRef[one.process.name] ?? 0) + 1;
      plmRef[one.process.platform] = (plmRef[one.process.name] ?? 0) + 1;
    }
    for (int i = 0, n = appList.length; i < n; i++) {
      final one = appList[i];
      final running = (appRef["${one.name}-${one.version}"] ?? 0) > 0;
      if (running != one.defaultRunning) {
        appList[i] = AppTile(
            name: one.name,
            version: one.version,
            platform: one.platform,
            defaultInstalled: one.defaultInstalled,
            defaultRunning: running
        );
      }
    }
    for (int i = 0, n = plmList.length; i < n; i++) {
      final one = plmList[i];
      final running = (plmRef["${one.name}-${one.version}"] ?? 0) > 0;
      if (running != one.defaultRunning) {
        plmList[i] = PlatformTile(
            name: one.name,
            version: one.version,
            defaultInstalled: one.defaultInstalled,
            defaultRunning: running
        );
      }
    }
  }

  @override
  void initState() {
    super.initState();

    downloadProgress = event.platformToken.stream.listen((msg) {
      final T = msg[0];
      if (T != "download") return;
      final tnv = msg[1];
      if (tnv is! String) return;
      if (!tnv.endsWith(".bin")) return;
      final parts = tnv.substring(0, tnv.length - 4).split("-");
      // TODO: check parts.length
      final type = parts[0] == "app" ? "application" : "platform";
      final name = "${parts[1]}-${parts[2]}";
      final url = msg[2];
      final filename = msg[3];
      final val = msg[4];
      try {
        final mark = downloadList.firstWhere(
          (one) => one.url == url &&
              one.filename == filename,
        );
        if (val >= 1) {
          final i = downloadList.indexOf(mark);
          if (i >= 0) {
            setState(() {
              downloadList.removeAt(i);
            });
          }
        } else if (val == -1) {
          // cancel or error
          final i = downloadList.indexOf(mark);
          if (i >= 0) {
            setState(() {
              downloadList.removeAt(i);
            });
          }
        }
      } catch (e) {
        if (val >= 0 && val < 1) {
          setState(() {
            downloadList.add(DownloadTile(name: name, type: type, url: url, filename: filename));
          });
        }
      }
    });

    applicationEvent = NodeBaseApi.event.receiveBroadcastStream("app").listen((message) {
      final cmd = message[0];
      final nameVersion = message[1];
      final app = nodebase.instance.application.getApp(nameVersion);
      AppRuntimeTile? tile;
      try {
        tile = runtimeList.firstWhere((one) => one.process == app || one.process.name == nameVersion);
      } catch(err) {
        tile = null;
      }
      switch(cmd) {
        case "start":
          if (app == null || tile != null) return;
          runtimeList.add(AppRuntimeTile(process: app));
          updateDependencyState();
          setState(() {});
          break;
        case "stop":
          if (tile == null) return;
          final i = runtimeList.indexOf(tile);
          runtimeList.removeAt(i);
          updateDependencyState();
          setState(() {});
          break;
      }
    });

    initPlatformList();
    initApplicationList();
  }

  @override
  void dispose() {
    downloadProgress.cancel();
    applicationEvent.cancel();
    super.dispose();
  }

  void initPlatformList() {
    (() async {
      final platform = nodebase.instance.platform;
      return await Future.wait([
        platform.listAvailablePlatformList(),
        platform.listInstalledPlatformList(),
      ]);
    })().then((list) {
      final [listAp, listIp] = list;
      final List<PlatformTile> r = [];
      listIp.forEach((name, versions) {
        for (final version in versions) {
          if (listAp.containsKey(name) && listAp[name]!.contains(version)) continue;
          final p = PlatformTile(
              name: name,
              version: version,
              defaultInstalled: true,
              userDefined: true
          );
          r.add(p);
        }
      });
      listAp.forEach((name, versions) {
        for (final version in versions) {
          final installed = listIp.containsKey(name) && listIp[name]!.contains(version);
          final p = PlatformTile(
              name: name,
              version: version,
              defaultInstalled: installed,
              userDefined: false
          );
          r.add(p);
        }
      });
      // TODO: sort r and installed ones should at top
      setState(() {
        plmList.clear();
        plmList.addAll(r);
      });
    });
  }

  void initApplicationList() {
    (() async {
      final platform = nodebase.instance.platform;
      return await Future.wait([
        platform.listAvailableApplicationList(),
        platform.listInstalledApplicationList(),
      ]);
    })().then((list) {
      final [listAa, listIa] = list;
      final List<AppTile> r = [];
      listIa.forEach((name, vps) {
        for (final vp in vps) {
          if (listAa.containsKey(name) && listAa[name]!.contains(vp)) continue;
          final i = vp.indexOf(':');
          final version = vp.substring(0, i);
          final platform = vp.substring(i+1);
          final a = AppTile(
              name: name,
              version: version,
              defaultInstalled: true,
              platform: platform,
              userDefined: true
          );
          r.add(a);
        }
      });
      listAa.forEach((name, vps) {
        for (final vp in vps) {
          final i = vp.indexOf(':');
          final version = vp.substring(0, i);
          final platform = vp.substring(i+1);
          final installed = listIa.containsKey(name) && listIa[name]!.contains(vp);
          final a = AppTile(
              name: name,
              version: version,
              defaultInstalled: installed,
              platform: platform,
              userDefined: false
          );
          r.add(a);
        }
      });
      setState(() {
        appList.clear();
        appList.addAll(r);
      });
    });
  }
  
  List<Widget> buildRunningView(BuildContext context) {
    final List<Widget> runningView = [
      const Row(
        mainAxisAlignment: MainAxisAlignment.start,
        children: [
          Wrap(
            spacing: 2,
            children: [
              Text("  "),
              Icon(Icons.directions_run),
              Text(" Running"),
            ],
          ),
        ],
      ),
    ];
    if (runtimeList.isNotEmpty) {
      runningView.add(
        ListView.builder(
            itemCount: runtimeList.length,
            shrinkWrap: true,
            itemBuilder: (BuildContext context, int index) => runtimeList[index]
        ),
      );
    }
    if (downloadList.isNotEmpty) {
      runningView.add(
        ListView.builder(
            itemCount: downloadList.length,
            shrinkWrap: true,
            itemBuilder: (BuildContext context, int index) => downloadList[index]
        ),
      );
    }
    return runningView;
  }
  
  List<Widget> buildApplicationView(BuildContext context) {
    final List<Widget> appView = [
      const Divider(),
      const Row(
        mainAxisAlignment: MainAxisAlignment.start,
        children: [
          Wrap(
            spacing: 2,
            children: [
              Text("  "),
              Icon(Icons.extension_rounded),
              Text(" Application"),
            ],
          ),
        ],
      ),
    ];
    if (appList.isNotEmpty) {
      appView.add(
        ListView.builder(
            itemCount: appList.length,
            shrinkWrap: true,
            itemBuilder: (BuildContext context, int index) => appList[index]
        ),
      );
    }
    return appView;
  }
  
  List<Widget> buildPlatformView(BuildContext context) {
    final List<Widget> plmView = [
      const Divider(),
      const Row(
        mainAxisAlignment: MainAxisAlignment.start,
        children: [
          Wrap(
            spacing: 2,
            children: [
              Text("  "),
              Icon(Icons.apps),
              Text(" Platform"),
            ],
          ),
        ],
      ),
    ];
    if (plmList.isNotEmpty) {
      plmView.add(
        ListView.builder(
            itemCount: plmList.length,
            shrinkWrap: true,
            itemBuilder: (BuildContext context, int index) => plmList[index]
        ),
      );
    }
    return plmView;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        // TRY THIS: Try changing the color here to a specific color (to
        // Colors.amber, perhaps?) and trigger a hot reload to see the AppBar
        // change color while the other colors stay the same.
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        // Here we take the value from the MyHomePage object that was created by
        // the App.build method, and use it to set our appbar title.
        title: const Text("NodeBase"),
      ),
      body: SingleChildScrollView(
        child: Column(
          children: [
            ...buildRunningView(context),
            ...buildApplicationView(context),
            ...buildPlatformView(context),
          ],
        ),
      ),
    );
  }
}