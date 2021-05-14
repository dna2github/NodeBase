import 'dart:convert';
import 'package:flutter/material.dart';
import './io.dart';
import './search.dart';
import './app_model.dart';
import './page_environment.dart';
import './page_platform.dart';
import './page_apps.dart';

class NodeBaseHomePage extends StatefulWidget {
  NodeBaseHomePage({Key key, this.title}) : super(key: key);

  final String title;

  @override
  _NodeBaseHomePageState createState() => _NodeBaseHomePageState();
}

class _NodeBaseHomePageState extends State<NodeBaseHomePage> {
  @override
  void initState() {
    super.initState();
    readAppFileAsString("/config.json").then((config) {
      if (config != "") {
        onReady(jsonDecode(config));
      }
    });
  }

  onReady(config) {
    print(config);
    if (config == null) return;
    // setState(() { _counter = config['counter']; });
  }

  onNavigate(NodeBaseAppModule module) {
    var route;
    switch (module.name) {
      case "Environment":
        {
          route = MaterialPageRoute(
              builder: (context) => NodeBaseEnvironmentSettings());
        }
        break;
      case "Platform":
        {
          route = MaterialPageRoute(
              builder: (context) => NodeBasePlatformSettings());
        }
        break;
      case "Application":
        {
          route =
              MaterialPageRoute(builder: (context) => NodeBaseApplications());
        }
        break;
    }
    Navigator.push(context, route);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
        appBar: AppBar(title: Text(widget.title), actions: <Widget>[
          IconButton(
              onPressed: () {
                showSearch(context: context, delegate: NodeBaseSearch());
              },
              icon: Icon(Icons.search))
        ]),
        body: ListView.builder(
            padding: const EdgeInsets.all(8),
            itemCount: NodeBaseAppModule.list.length,
            itemBuilder: (BuildContext context, int index) {
              return Container(
                  child: Card(
                      child: ListTile(
                          onTap: () =>
                              onNavigate(NodeBaseAppModule.list[index]),
                          title: Text('${NodeBaseAppModule.list[index].name}'),
                          subtitle:
                              Text('${NodeBaseAppModule.list[index].desc}'),
                          leading: IconButton(
                            icon: NodeBaseAppModule.list[index].icon,
                          ) // IconButton
                          ) // ListTile
                      ) // Card
                  );
            }) // body
        );
  }
}
