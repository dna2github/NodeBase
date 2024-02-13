import 'dart:async';
import 'package:flutter/material.dart';
import 'package:path/path.dart' as path;

import '../ctrl/application.dart';
import '../ctrl/nodebase.dart' as nodebase;

Future<Map<String, dynamic>> runAppInit(BuildContext context, Map<String, dynamic> config) async {
  final name = config["name"] ?? "";
  final version = config["version"] ?? "";
  final platform = config["platform"] ?? "";
  config["ready"] = false;
  if (name == "" || version == "" || platform == "") return config;
  config["base"] = await nodebase.instance.platform.getApplicationBaseDir(name, version);
  // meta.entryPoint[0]
  final meta = await nodebase.instance.platform.readApplicationMetaJson(name, version);
  config["availableEntryPoint"] = meta["entryPoint"] ?? [];
  config["argRequire"] = meta["argRequire"] ?? [];
  config["envRequire"] = meta["envRequire"] ?? [];
  // savedConfig.arg, savedConfig.env
  final savedConfig = await nodebase.instance.platform.readApplicationConfig(name, version);
  config["lastArg"] = savedConfig["arg"] ?? [];
  config["lastEnv"] = savedConfig["env"] ?? {};
  config["lastEntryPoint"] = savedConfig["entryPoint"];
  config["lastExec"] = savedConfig["exec"];
  config["lastPlatformVersion"] = savedConfig["platformVersion"];
  // installed: {name: [version,...]}
  final installed = await nodebase.instance.platform.listInstalledPlatformList();
  config["platformList"] = {};
  config["platformList"][platform] = installed[platform];
  config["ready"] = true;
  return config;
}

Future<Map<String, dynamic>> runAppStepCheckPlatform(BuildContext context, Map<String, dynamic> config) async {
  final name = config["name"];
  final version = config["version"];
  final platform = config["platform"];
  if (config["platformList"][platform] == null) {
    // no install, check available and guide to download
    await downloadPlatform(context, config);
    config["platformReady"] = false;
  } else {
    // by default select last one, guide user to select platform version
    config = await selectPlatform(context, config);
    config["platformReady"] = true;
  }
  return config;
}
Future<void> downloadPlatform(BuildContext context, Map<String, dynamic> config) async {
  // TODO: show all; click one download and stop app running
}
Future<List<String>> getPlatformExec(String name, String version) async {
  Map<String, dynamic> meta = await nodebase.instance.platform.readPlatformMetaJson(name, version);
  List<String> r = [];
  for (final one in meta["executable"]) {
    r.add(one);
  }
  return r;
}
Future<Map<String, dynamic>> selectPlatform(BuildContext context, Map<String, dynamic> config) async {
  final Completer ok = Completer();
  final platform = config["platform"];
  final platformList = config["platformList"][platform];
  final List<DropdownMenuItem<String>> dropdownItems = [
    const DropdownMenuItem<String>(value: "-", child: Text("(not selected)")),
  ];
  for (final value in platformList) {
    dropdownItems.add(DropdownMenuItem<String>(value: value, child: Text(value)));
  }
  String selected = "-";
  String selectedExec = "-";
  List<String> execs = [];
  showDialog(context: context, builder: (context) {
    return StatefulBuilder(
        builder: (context, setState) {
          return AlertDialog(
            title: const Text("Select Platform"),
            shape: const BeveledRectangleBorder(),
            content: SizedBox(
              height: 200,
              width: 300,
              child: SingleChildScrollView(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text("Platform: $platform"),
                    DropdownButton<String>(
                      value: selected,
                      items: dropdownItems,
                      onChanged: (val) {
                        if (val == null) return;
                        selected = val;
                        if (val == "-") {
                          execs = [];
                          setState(() {});
                          return;
                        }
                        getPlatformExec(platform, selected).then((list) {
                          execs = list;
                          setState(() {});
                        });
                      },
                    ),
                    ...(selected == "-" ? [] : [
                      const Text("Exec"),
                      DropdownButton(items: [
                        const DropdownMenuItem<String>(value: "-", child: Text("(not selected)")),
                        ...execs.map((name) => DropdownMenuItem(
                          value: name,
                          child: Text(path.basename(name), overflow: TextOverflow.ellipsis),
                        )).toList()
                      ], onChanged: (val) {
                        if (val == null) return;
                        selectedExec = val;
                        setState(() {});
                      }, value: selectedExec),
                    ]),
                  ],
                ),
              ),
            ),
            actions: [
              TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text("Cancel")),
              ElevatedButton(
                  onPressed: selected == "-" || selectedExec == "-" ? null : () {
                    Navigator.of(context).pop();
                    ok.complete();
                  },
                  child: const Text("Next")),
            ],
          );
        }
    );
  }
  );
  await ok.future;
  config["platformVersion"] = selected;
  config["exec"] = path.join(
      await nodebase.instance.platform.getPlatformBaseDir(platform, selected),
      selectedExec
  );
  return config;
}

Future<Map<String, dynamic>> runAppStepArgAndEnv(BuildContext context, Map<String, dynamic> config) async {
  final name = config["name"];
  final version = config["version"];
  final platform = config["platform"];
  // TODO: show component to edit arg (List<String>) and env(Map<String, String>)
  return config;
}

Future<ApplicationProcess> runAppStepReview(BuildContext context, Map<String, dynamic> config) async {
  final name = config["name"];
  final version = config["version"];
  final platform = config["platform"];
  final arg = config["arg"] ?? [];
  final env = config["env"] ?? {};
  final entryPoint = config["entryPoint"]; // should not null
  final exec = config["exec"]; // should not null
  // TODO: mark platform is running
  final app = nodebase.instance.application.startProcess(
      "$name-$version", [exec, entryPoint, ...arg], env
  );
  return app;
}

Future<void> saveAppConfig(Map<String, dynamic> config) async {
  final name = config["name"];
  final version = config["version"];
  Map<String, dynamic> json = {};
  json["arg"] = config["arg"];
  json["env"] = config["env"];
  json["entryPoint"] = config["entryPoint"];
  json["exec"] = config["exec"];
  json["platform"] = config["platform"];
  json["platformVersion"] = config["platformVersion"];
  await nodebase.instance.platform.writeApplicationConfig(name, version, json);
}