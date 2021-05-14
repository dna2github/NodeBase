import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import './api.dart';

String BASE_HOST = 'raw.githubusercontent.com';
String BASE_URL = '/wiki/dna2github/NodeBase';

class PlatformItem {
  String arch;
  String name;
  bool zip;
}

class NodeBasePlatformMarketItem extends StatefulWidget {
  final Function(PlatformItem item) fnInstall;
  final Function(PlatformItem item) fnUninstall;
  final PlatformItem item;

  NodeBasePlatformMarketItem(
      {Key key, this.item, this.fnInstall, this.fnUninstall})
      : super(key: key);

  @override
  _NodeBasePlatformMarketItemState createState() =>
      _NodeBasePlatformMarketItemState();
}

class _NodeBasePlatformMarketItemState
    extends State<NodeBasePlatformMarketItem> {
  @override
  Widget build(BuildContext context) {
    return Card(
        child: ListTile(
            title: Text(widget.item.name),
            subtitle: Text(widget.item.arch),
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
                    ]) // PopupMenuButton
            ));
  }
}

class NodeBasePlatformMarket extends StatefulWidget {
  NodeBasePlatformMarket({Key key}) : super(key: key);
  @override
  _NodeBasePlatformMarketState createState() => _NodeBasePlatformMarketState();
}

class _NodeBasePlatformMarketState extends State<NodeBasePlatformMarket> {
  List<NodeBasePlatformMarketItem> entities = [];
  bool loading = true;

  @override
  void initState() {
    _showPlatformItems();
  }

  Future<http.Response> fetchPlatformList() async {
    return http
        .get(Uri.https(BASE_HOST, BASE_URL + '/quick/platform/meta.list'));
  }

  Future<http.Response> downloadFile(String uri) async {
    return http.get(Uri.https(BASE_HOST, BASE_URL + uri));
  }

  Future<List<PlatformItem>> _getPlatformItems() async {
    final res = await fetchPlatformList();
    List<PlatformItem> r = [];
    for (String line in res.body.split("\n")) {
      if (line.length == 0) continue;
      final parts = line.split(" ");
      // arch name zip?
      final one = PlatformItem();
      one.arch = parts[0];
      one.name = parts[1];
      if (parts.length == 3 && parts[2] == "zip") {
        one.zip = true;
      }
      r.add(one);
    }
    return r;
  }

  installPlatformItem (PlatformItem item) {}

  uninstallPlatformItem (PlatformItem item) {}

  Future<void> _showPlatformItems() async {
    final items = await _getPlatformItems();
    final List<NodeBasePlatformMarketItem> list = [];
    for (PlatformItem item in items) {
      final tile = NodeBasePlatformMarketItem(
        item: item,
        fnInstall: installPlatformItem,
        fnUninstall: uninstallPlatformItem,
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
            title: Text('Platform Market'),
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
            })
    );
  }
}
