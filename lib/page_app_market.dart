import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import './api.dart';
import './io.dart';

String BASE_HOST = 'raw.githubusercontent.com';
String BASE_URL = '/wiki/dna2github/NodeBase';

class AppItem {
  String platform = "";
  String name = "";
  bool zip = false;
}

class NodeBaseAppMarketItem extends StatefulWidget {
  final Function(AppItem item) fnInstall;
  final Function(AppItem item) fnUninstall;
  final AppItem item;

  NodeBaseAppMarketItem({
    required this.item,
    required this.fnInstall,
    required this.fnUninstall,
    super.key
  });

  @override
  _NodeBaseAppMarketItemState createState() =>
      _NodeBaseAppMarketItemState();
}

class _NodeBaseAppMarketItemState extends State<NodeBaseAppMarketItem> {
  @override
  Widget build(BuildContext context) {
    return Card(
        child: ListTile(
            title: Text(widget.item.name),
            subtitle: Text(widget.item.platform),
            trailing: PopupMenuButton<int>(
                icon: Icon(Icons.more_vert),
                onSelected: (int result) {
                  switch (result) {
                    case 101:
                      {
                        widget.fnInstall(widget.item);
                      }
                      break;
                  }
                },
                itemBuilder: (BuildContext context) => <PopupMenuEntry<int>>[
                      const PopupMenuItem<int>(
                          value: 101, child: Text('Install')),
                      // TODO: add Uninstall
                    ]) // PopupMenuButton
            ));
  }
}

class NodeBaseAppMarket extends StatefulWidget {
  NodeBaseAppMarket({super.key});
  @override
  _NodeBaseAppMarketState createState() => _NodeBaseAppMarketState();
}

class _NodeBaseAppMarketState extends State<NodeBaseAppMarket> {
  List<NodeBaseAppMarketItem> entities = [];
  bool loading = true;

  @override
  void initState() {
    _showAppItems();
  }

  Future<http.Response> fetchAppList() async {
    return http.get(Uri.https(BASE_HOST, BASE_URL + '/quick/app/meta.list'));
  }

  Future<List<AppItem>> _getAppItems() async {
    final res = await fetchAppList();
    List<AppItem> r = [];
    for (String line in res.body.split("\n")) {
      if (line.length == 0) continue;
      final parts = line.split(" ");
      // platform name zip?
      final one = AppItem();
      one.platform = parts[0];
      one.name = parts[1];
      if (parts.length == 3 && parts[2] == "zip") {
        one.zip = true;
      } else {
        one.zip = false;
      }
      r.add(one);
    }
    return r;
  }

  installAppItem(AppItem item) async {
    final url = "https://" +
        BASE_HOST +
        BASE_URL +
        "/quick/app/" +
        item.platform +
        "/" +
        item.name +
        (item.zip ? ".zip" : "");
    print(url);
    try {
      var dst = await NodeBaseApi.fetchApp(url);
      var config = await readAppFileAsString("/apps.json");
      if (config == "") config = "{\"apps\": []}";
      final data = jsonDecode(config);
      final list = data["apps"].toList();
      list.add({"name": item.name, "path": dst, "platform": item.platform});
      await writeAppFileAsString(
          "/apps.json",
          JsonEncoder((x) {
            return x;
          }).convert({"apps": list}));
    } catch (e) {
      // TODO: handle exception
    }
  }

  uninstallAppItem(AppItem item) {}

  Future<void> _showAppItems() async {
    final items = await _getAppItems();
    final List<NodeBaseAppMarketItem> list = [];
    for (AppItem item in items) {
      final tile = NodeBaseAppMarketItem(
        item: item,
        fnInstall: installAppItem,
        fnUninstall: uninstallAppItem,
      );
      list.add(tile);
    }
    setState(() {
      entities.clear();
      entities.addAll(list);
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
        appBar: AppBar(
            title: Text('Application Market'),
            leading: IconButton(
                icon: Icon(Icons.arrow_back),
                onPressed: () {
                  Navigator.pop(context);
                })),
        body: ListView.builder(
            padding: const EdgeInsets.all(8),
            itemCount: entities.length,
            itemBuilder: (BuildContext context, int index) {
              return Container(child: entities[index]);
            }));
  }
}
