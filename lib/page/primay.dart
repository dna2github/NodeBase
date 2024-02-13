import 'dart:async';

import 'package:flutter/material.dart';
import '../util/event.dart' as event;
import '../ctrl/nodebase.dart' as nodebase;
import '../comp/AppRuntimeTile.dart';
import '../comp/DownloadTile.dart';
import '../comp/AppTile.dart';
import '../comp/PlatformTile.dart';

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
      print(parts);
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

    initPlatformList();
    initApplicationList();
  }

  @override
  void dispose() {
    downloadProgress.cancel();
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
      listAp.forEach((name, versions) {
        for (final version in versions) {
          final installed = listIp.containsKey(name) && listIp[name]!.contains(version);
          final p = PlatformTile(name: name, version: version, defaultInstalled: installed);
          r.add(p);
        }
      });
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
      listAa.forEach((name, vps) {
        for (final vp in vps) {
          final i = vp.indexOf(':');
          final version = vp.substring(0, i);
          final platform = vp.substring(i+1);
          final installed = listIa.containsKey(name) && listIa[name]!.contains(vp);
          final a = AppTile(name: name, version: version, defaultInstalled: installed, platform: platform);
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
    final runningApps = nodebase.instance.application.getRunningApp();
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
    if (runningApps.isNotEmpty) {
      runningView.add(
        ListView.builder(
            itemCount: runningApps.length,
            shrinkWrap: true,
            itemBuilder: (BuildContext context, int index) =>
                AppRuntimeTile(process: runningApps[index])
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