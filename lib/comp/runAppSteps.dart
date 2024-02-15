import 'dart:async';
import 'package:flutter/material.dart';
import 'package:path/path.dart' as path;

import '../ctrl/application.dart';
import '../ctrl/nodebase.dart' as nodebase;

// TODO: rewrite all async, incomplete await causes memory leak

const defaultHintStyle = TextStyle(color: Color.fromARGB(128, 0, 0, 0));

class ArgInput extends StatelessWidget {
  const ArgInput({
    super.key,
    required this.onDelete,
    required this.onChanged,
    this.placeholder = "",
    this.initialValue = "",
  });

  final String placeholder;
  final String initialValue;
  final void Function(ArgInput)? onDelete;
  final void Function(ArgInput, String)? onChanged;

  @override
  Widget build(BuildContext context) {
    return Row(
      textDirection: TextDirection.rtl,
      children: [
        IconButton(
            onPressed: onDelete == null ? null : () => onDelete!(this),
            icon: const Icon(Icons.close)
        ),
        Expanded(child: TextFormField(
          initialValue: initialValue,
          onChanged: onChanged == null ? null : (val) => onChanged!(this, val),
          decoration: InputDecoration(
              hintText: placeholder,
              hintStyle: defaultHintStyle
          ),
        )),
      ],
    );
  }

}

class EnvInput extends StatelessWidget {
  const EnvInput({
    super.key,
    required this.onDelete,
    required this.onKeyChanged,
    required this.onValChanged,
    this.placeholder = "",
    this.initialKeyValue = "",
    this.initialValValue = "",
  });

  final String placeholder;
  final String initialKeyValue;
  final String initialValValue;
  final void Function(EnvInput)? onDelete;
  final void Function(EnvInput, String)? onKeyChanged;
  final void Function(EnvInput, String)? onValChanged;

  @override
  Widget build(BuildContext context) {
    return Row(
      textDirection: TextDirection.rtl,
      children: [
        IconButton(
            onPressed: onDelete == null ? null : () => onDelete!(this),
            icon: const Icon(Icons.close)
        ),
        Expanded(child: TextFormField(
          initialValue: initialValValue,
          onChanged: onValChanged == null ? null : (val) => onValChanged!(this, val),
          decoration: InputDecoration(
              hintText: placeholder,
              hintStyle: defaultHintStyle
          ),
        )),
        Expanded(child: TextFormField(
          initialValue: initialKeyValue,
          onChanged: onKeyChanged == null ? null : (val) => onKeyChanged!(this, val),
        )),
      ],
    );
  }

}

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

  final lastArg = savedConfig["arg"] ?? [];
  config["lastArg"] = [];
  for(int i = 0, n = lastArg.length, m = config["argRequire"].length; i < n || i < m; i++) {
    if (i < m && i < n) {
      final one = config["argRequire"][i];
      config["lastArg"].add({
        "help": one["help"] ?? "",
        "default": one["default"] ?? "",
        "last": lastArg[i],
        "required": one["required"] ?? true,
      });
    } else if (i < m && i >= n) {
      final one = config["argRequire"][i];
      config["lastArg"].add({
        "help": one["help"] ?? "",
        "default": one["default"] ?? "",
        "last": null,
        "required": one["required"] ?? true,
      });
    } else {
      config["lastArg"].add({
        "help": "",
        "default": "",
        "last": lastArg[i],
        "required": false,
      });
    }
  }

  final lastEnv = savedConfig["env"] ?? {};
  config["lastEnv"] = {};
  for(int i = 0, n = config["envRequire"].length; i < n; i++) {
    final one = config["envRequire"][i];
    final name = one["name"];
    config["lastEnv"][name] = {
      "name": name,
      "help": one["help"] ?? "",
      "default": one["default"],
      "last": lastEnv[name] ?? "",
      "required": one["required"] ?? false,
    };
  }
  for(final k in lastEnv.keys) {
    final one = config["lastEnv"][k];
    if (one == null) {
      config["lastEnv"][k] = {
        "name": k,
        "help": "",
        "default": "",
        "last": lastEnv[k],
        "required": false,
      };
    }
  }

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
                        ))
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

ArgInput generateArgInput(List<dynamic> argItems, List<String> arg, dynamic one, Function setState) {
  return ArgInput(onDelete: (self) {
    final i = argItems.indexOf(self);
    argItems.removeAt(i);
    arg.removeAt(i);
    setState(() {});
  }, onChanged: (self, val) {
    final i = argItems.indexOf(self);
    arg[i] = val;
  }, placeholder: one["help"] ?? "", initialValue: one["last"] ?? one["default"] ?? "");
}
EnvInput generateEnvInput(List<dynamic> envItems, List<String> envk, List<String> envv, dynamic one, Function setState) {
  return EnvInput(onDelete: (self) {
    final i = envItems.indexOf(self);
    envItems.removeAt(i);
    envk.removeAt(i);
    envv.removeAt(i);
    setState(() {});
  }, onKeyChanged: (self, val) {
    final i = envItems.indexOf(self);
    envk[i] = val;
  }, onValChanged: (self, val) {
    final i = envItems.indexOf(self);
    envv[i] = val;
  }, placeholder: one["help"] ?? "",
    initialKeyValue: one["name"] ?? "",
    initialValValue: one["last"] ?? one["default"] ?? "",
  );
}

Future<Map<String, dynamic>> runAppStepArgAndEnv(BuildContext context, Map<String, dynamic> config) async {
  final Completer ok = Completer();
  final name = config["name"];
  final version = config["version"];
  final platform = config["platform"];
  // TODO: show component to edit arg (List<String>) and env(Map<String, String>)
  final availableEntryPoint = config["availableEntryPoint"];
  final List<DropdownMenuItem<String>> dropdownItems = [
    const DropdownMenuItem<String>(value: "-", child: Text("(not selected)")),
  ];
  for (final value in availableEntryPoint) {
    dropdownItems.add(DropdownMenuItem<String>(value: value, child: Text(value)));
  }
  String selected = "-";
  List<String> arg = [];
  List<String> envk = [];
  List<String> envv = [];
  final List<dynamic> argItems = [];
  final List<dynamic> envItems = [];
  for (final _ in config["lastArg"]) {
    argItems.add(null);
    arg.add("");
  }
  for (final _ in config["lastEnv"].values) {
    envItems.add(null);
  }

  showDialog(context: context, builder: (context) {
    return StatefulBuilder(
        builder: (context, setState) {
          int i = 0;
          for (final one in config["lastArg"]) {
            if (i >= argItems.length) break;
            argItems[i] = generateArgInput(argItems, arg, one, setState);
            arg[i] = one["last"] ?? one["default"] ?? "";
            i ++;
          }
          i = 0;
          for (final one in config["lastEnv"].values) {
            if (i >= envItems.length) break;
            envItems[i] = generateEnvInput(envItems, envk, envv, one, setState);
            envv[i] = one["name"] ?? "";
            envk[i] = one["last"] ?? one["default"] ?? "";
            i ++;
          }
          return AlertDialog(
            title: const Text("Config Application"),
            shape: const BeveledRectangleBorder(),
            content: SizedBox(
              height: 200,
              width: 300,
              child: SingleChildScrollView(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const Text("Entrypoint"),
                    DropdownButton<String>(
                      value: selected,
                      items: dropdownItems,
                      onChanged: (val) {
                        if (val == null) return;
                        selected = val;
                        setState(() {});
                      },
                    ),
                    ...(selected == "-" ? []: [
                      const Text("Arg"),
                      ...argItems,
                      TextButton(onPressed: () {
                        final newOne = generateArgInput(argItems, arg, {}, setState);
                        argItems.add(newOne);
                        arg.add("");
                        setState(() {});
                      }, child: const Text("+")),
                      const Text("Env"),
                      ...envItems,
                      TextButton(onPressed: () {
                        final newOne = generateEnvInput(envItems, envk, envv, {}, setState);
                        envItems.add(newOne);
                        envk.add("");
                        envv.add("");
                        setState(() {});
                      }, child: const Text("+")),
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
                  onPressed: selected == "-" ? null : () {
                    Navigator.of(context).pop();
                    ok.complete();
                  },
                  child: const Text("Next")),
            ],
          );
        });
  });
  await ok.future;
  config["entryPoint"] = path.join(
      await nodebase.instance.platform.getApplicationBaseDir(name, version),
      selected
  );

  config["arg"] = arg;

  Map<String, String> env = {};
  for (int i = 0, n = envk.length; i < n; i++) {
    final k = envk[i];
    final v = envv[i];
    if (k == "" || v == "") continue;
    env[k] = v;
  }
  config["env"] = env;
  return config;
}

Future<ApplicationProcess?> runAppStepReview(BuildContext context, Map<String, dynamic> config) async {
  final ok = Completer();
  final name = config["name"];
  final version = config["version"];
  final platform = config["platform"];
  final platformVersion = config["platformVersion"];
  final arg = config["arg"] ?? [];
  final env = config["env"] ?? {};
  final entryPoint = config["entryPoint"]; // should not null
  final exec = config["exec"]; // should not null

  showDialog(context: context, builder: (_) => AlertDialog(
    title: const Text("Application Info"),
    shape: const BeveledRectangleBorder(),
    content: SizedBox(
      height: 200,
      width: 300,
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SelectableText("-- Application --\n"
                "$name-$version\n\\-> ${path.basename(entryPoint)}\n\n"
                "-- Platform --\n$platform-$platformVersion\n\\-> ${path.basename(exec)}\n\n"
                "-- Config --\n"
                "-> Arg\n${arg.isEmpty ? "(empty)" : arg.join("\n")}\n"
                "-> Env\n${env.isEmpty ? "(empty)" : env.entries.map(
                    (item) => "${item.key} = ${item.value}").toList().join("\n")}"
            ),
          ],
        ),
      ),
    ),
    actions: [
      TextButton(
          onPressed: () {
            Navigator.of(context).pop();
            ok.complete(false);
          },
          child: const Text("Cancel")),
      ElevatedButton(
          onPressed: () {
            Navigator.of(context).pop();
            ok.complete(true);
          },
          child: const Text("Run")),
    ],
  ));
  final r = await ok.future;
  if (!r) return null;
  // TODO: mark platform is running
  final app = nodebase.instance.application.startProcess(
      "$name-$version", "$platform-$platformVersion", [exec, entryPoint, ...arg], env
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