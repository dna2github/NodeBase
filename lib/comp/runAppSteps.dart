import 'package:flutter/material.dart';
import 'package:nodebase/ctrl/application.dart';
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
Future<Map<String, dynamic>> selectPlatform(BuildContext context, Map<String, dynamic> config) async {
  // TODO: show available; when select one, show available exec
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