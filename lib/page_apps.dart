import 'dart:convert';
import 'package:flutter/material.dart';
import './io.dart';
import './app_model.dart';
import './api.dart';

class NodeBaseAppItem extends StatefulWidget {
  final Function(NodeBaseAppItem item) fnRemove;
  final Function() fnSaveConfig;
  NodeBaseApp item;
  bool isEdit = false;
  bool isCreated = false;

  NodeBaseAppItem({Key key, this.item, this.fnRemove, this.fnSaveConfig}): super(key: key);

  @override
  _NodeBaseAppItemState createState() => _NodeBaseAppItemState();
}
class _NodeBaseAppItemState extends State<NodeBaseAppItem> {

  final ctrlName = TextEditingController();
  final ctrlPlatform = TextEditingController();
  bool _initialized = false;

  @override
  void dispose() {
    ctrlName.dispose();
    ctrlPlatform.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (widget.isEdit) {
      if (!_initialized) {
        ctrlName.text = widget.item.name;
        ctrlPlatform.text = widget.item.platform;
        _initialized = true;
      }
      final entities = <Widget>[
        ListTile(
          leading: Icon(Icons.bookmark),
          title: TextField(
            controller: ctrlName,
            decoration: InputDecoration( labelText: 'Name' )
          )
        ),
        ListTile(
          leading: Icon(Icons.cloud_queue),
          title: TextField(
            controller: ctrlPlatform,
            decoration: InputDecoration( labelText: 'Platform' )
          )
        ) // ListTile
      ];
      if (widget.item.path != null && widget.item.path != "") {
        entities.add(ListTile(
          leading: SizedBox(width: 5),
          title: Text(widget.item.path)
        ));
      }
      entities.add(
        Row(
          children: <Widget>[
            FlatButton.icon(
              icon: Icon(Icons.check),
              label: Text("Save"),
              onPressed: () {
                if (ctrlName.text == "") return;
                setState(() {
                  widget.item.name = ctrlName.text;
                  widget.item.platform = ctrlPlatform.text;
                  widget.isCreated = false;
                  widget.isEdit = false;
                  widget.fnSaveConfig();
                });
              }
            ),
            FlatButton.icon(
              icon: Icon(Icons.close),
              label: Text("Cancel"),
              onPressed: () {
                if (widget.isCreated) {
                  widget.fnRemove(widget);
                } else {
                  setState(() { widget.isEdit = false; });
                }
              }
            )
          ]
        ) // Row
      );
      return Card(
        child: Column(
          children: entities
        ) // ListView
      );
    }
    var name = widget.item.name == null?"":widget.item.name;
    var platform = widget.item.platform == null?"":widget.item.platform;
    return Card(
      child: ListTile(
        title: Text(name),
        subtitle: Text(platform),
        trailing: PopupMenuButton<int>(
          icon: Icon(Icons.more_vert),
          onSelected: (int result) {
            switch(result) {
              case 101: {
                setState(() { widget.isEdit = true; });
              } break;
              case 102: {
                // TODO: if we remove this item, do we need also remove the file at
                //       widget.item.path?
                widget.fnRemove(widget);
                widget.fnSaveConfig();
              } break;
            }
          },
          itemBuilder: (BuildContext context) => <PopupMenuEntry<int>>[
            const PopupMenuItem<int>( value: 101, child: Text('Edit') ),
            const PopupMenuItem<int>( value: 102, child: Text('Delete') )
          ]
        ) // PopupMenuButton
      )
    );
  }
}

class NodeBaseApplications extends StatefulWidget {
  NodeBaseApplications({Key key}): super(key: key);
  @override
  _NodeBaseApplicationsState createState() => _NodeBaseApplicationsState();
}
class _NodeBaseApplicationsState extends State<NodeBaseApplications> {
  List<NodeBaseAppItem> entities = [];
  var loading = true;

  @override
  void initState () {
    super.initState();
    loadConfig();
  }

  loadConfig() async {
    setState(() { loading = true; });
    var config = await readAppFileAsString("/apps.json");
    final List<NodeBaseAppItem> list = <NodeBaseAppItem>[];
    if (config != "") {
      final data = jsonDecode(config);
      entities.clear();
      data['apps'].toList().forEach((x) {
        final item = NodeBaseApp(name: x['name']);
        item.path = x['path'];
        item.platform = x['platform'];
        final NodeBaseAppItem node = makeItem(item);
        list.add(node);
      });
    }
    setState(() {
      entities.addAll(list);
      loading = false;
    });
  }

  saveConfig() async {
    setState(() { loading = true; });
    await writeAppFileAsString("/apps.json", JsonEncoder((x) {
      if (x is NodeBaseAppItem) {
        return {
          "name": x.item.name,
          "path": x.item.path,
          "platform": x.item.platform
        };
      }
      return null;
    }).convert({
        "apps": entities
    }));
    setState(() { loading = false; });
  }

  removeItem(NodeBaseAppItem item) {
    final index = entities.indexOf(item);
    if (index < 0) return;
    setState(() {
      entities.removeAt(index);
    });
  }

  makeItem(NodeBaseApp item) {
    return NodeBaseAppItem(item: item, fnRemove: removeItem, fnSaveConfig: saveConfig);
  }

  @override
  Widget build(BuildContext context) {
    if (loading) {
      return Scaffold(
        body: Center( child: CircularProgressIndicator(
          semanticsLabel: "Loading ..."
        ) )
      );
    }
    return Scaffold(
      appBar: AppBar(
        title: Text('Applications'),
        leading: IconButton(
          icon: Icon(Icons.arrow_back),
          onPressed: () { Navigator.pop(context); }
        )
      ),
      body: (entities.length == 0)
        ? Center( child: Text('No application.') )
        : ListView.builder(
          padding: const EdgeInsets.all(8),
          itemCount: entities.length,
          itemBuilder: (BuildContext context, int index) {
            return Container( child: entities[index] );
          }), // body
      floatingActionButton: FloatingActionButton(
        tooltip: 'Add Application',
        child: Icon(Icons.add),
        onPressed: () {
          final item = NodeBaseApp(name: "");
          final entity = makeItem(item);
          setState(() {
            entity.isEdit = true;
            entity.isCreated = true;
            entities.add(entity);
          });
        }
      )
    );
  }
}
