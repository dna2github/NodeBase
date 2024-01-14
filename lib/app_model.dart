import 'package:flutter/material.dart';

class NodeBaseAppModule {
  NodeBaseAppModule({
    required this.id,
    required this.icon,
    required this.name,
    required this.desc,
  }) {}

  final int id;
  final String name;
  final String desc;
  final Icon icon;

  static final List<NodeBaseAppModule> list = <NodeBaseAppModule>[
    NodeBaseAppModule(
        id: 101,
        icon: Icon(Icons.settings),
        name: "Environment",
        desc: "application configurations."),
    NodeBaseAppModule(
        id: 102,
        icon: Icon(Icons.settings),
        name: "Platform",
        desc: "platform management, like node, go, python, ..."),
    NodeBaseAppModule(
        id: 102,
        icon: Icon(Icons.apps),
        name: "Application",
        desc: "application management, like running, developing, sharing, ...")
  ];
}

class NodeBasePlatform {
  NodeBasePlatform({required this.name}) {}

  String name = "";
  String path = "";
  String updateUrl = "";
}

class NodeBaseApp {
  NodeBaseApp({required this.name}) {}

  String name = "";
  String path = "";
  String platform = "";
}

class NodeBaseAppDetails {
  String path = "";
  // e.g. 127.0.0.1, 0.0.0.0
  String host = "";
  // e.g. 9090
  int port = 0;
  // e.g. index.js
  String entry = "";
  // e.g. /index.html
  String home = "";
}
