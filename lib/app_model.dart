import 'package:flutter/material.dart';

class NodeBaseAppModule {
  NodeBaseAppModule({Key key, this.id, this.icon, this.name, this.desc}) {}

  final int    id;
  final String name;
  final String desc;
  final Icon   icon;

  static final List<NodeBaseAppModule> list = <NodeBaseAppModule>[
    NodeBaseAppModule(
      id: 101, icon: Icon(Icons.settings), name: "Environment",
      desc: "application configurations."
    ),
    NodeBaseAppModule(
      id: 102, icon: Icon(Icons.settings), name: "Platform",
      desc: "platform management, like node, go, python, ..."
    ),
    NodeBaseAppModule(
      id: 102, icon: Icon(Icons.apps), name: "Application",
      desc: "application management, like running, developing, sharing, ..."
    )
  ];
}
