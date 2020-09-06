import 'dart:convert';
import 'package:flutter/material.dart';
import './io.dart';
import './app_model.dart';
import './api.dart';

class NodeBasePlatformItem extends StatefulWidget {
  final Function(NodeBasePlatformItem item) fnRemove;
  final Function() fnSaveConfig;
  NodeBasePlatform item;
  bool isEdit = false;
  bool isCreated = false;

  NodeBasePlatformItem({Key key, this.item, this.fnRemove, this.fnSaveConfig}): super(key: key);

  @override
  _NodeBasePlatformItemState createState() => _NodeBasePlatformItemState();
}
class _NodeBasePlatformItemState extends State<NodeBasePlatformItem> {

  final ctrlName = TextEditingController();
  final ctrlVersion = TextEditingController();
  final ctrlDownloadUrl = TextEditingController();
  bool _initialized = false;

  @override
  void dispose() {
    ctrlName.dispose();
    ctrlVersion.dispose();
    ctrlDownloadUrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (widget.isEdit) {
      if (!_initialized) {
        ctrlName.text = widget.item.name;
        ctrlVersion.text = widget.item.version;
        ctrlDownloadUrl.text = widget.item.updateUrl;
        _initialized = true;
      }
      final entities = <Widget>[
        ListTile(
          leading: Icon(Icons.call_to_action),
          title: TextField(
            controller: ctrlName,
            decoration: InputDecoration( labelText: 'Name' )
          )
        ),
        ListTile(
          leading: SizedBox(width: 5),
          title: TextField(
            controller: ctrlVersion,
            decoration: InputDecoration( labelText: 'Version' )
          )
        ),
        ListTile(
          leading: Icon(Icons.attachment),
          title: TextField(
            controller: ctrlDownloadUrl,
            decoration: InputDecoration( labelText: 'Download URL' )
          ),
          trailing: IconButton(
            icon: Icon(Icons.file_download),
            onPressed: () {
              if (ctrlDownloadUrl.text == "") return;
              NodeBaseApi.fetchExecutable(ctrlDownloadUrl.text).then((dst) {
                setState(() {
                  widget.item.path = dst;
                  if (!widget.isCreated) widget.fnSaveConfig();
                });
              });
            }
          ) // trailing
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
                  widget.item.version = ctrlVersion.text;
                  widget.item.updateUrl = ctrlDownloadUrl.text;
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
    var version = widget.item.version == null?"":"${widget.item.version}";
    if (version == "") version = ": N/A"; else version = ": ${version}";
    var path = widget.item.path == null?"":widget.item.path;
    if (path == "") {
      var url = widget.item.updateUrl == null?"":widget.item.updateUrl;
      if (url != "") path = "Remotely available @ ${url}";
                else path = "Not yet configured.";
    }
    return Card(
      child: ListTile(
        title: Text("${name}${version}"),
        subtitle: Text(path),
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

class NodeBasePlatformSettings extends StatefulWidget {
  NodeBasePlatformSettings({Key key}): super(key: key);
  @override
  _NodeBasePlatformSettingsState createState() => _NodeBasePlatformSettingsState();
}
class _NodeBasePlatformSettingsState extends State<NodeBasePlatformSettings> {
  List<NodeBasePlatformItem> entities = [];
  var loading = true;

  @override
  void initState () {
    super.initState();
    loadConfig();
  }

  loadConfig() async {
    setState(() { loading = true; });
    var config = await readAppFileAsString("/platform.json");
    final List<NodeBasePlatformItem> list = <NodeBasePlatformItem>[];
    if (config != "") {
      final data = jsonDecode(config);
      entities.clear();
      data['platforms'].toList().forEach((x) {
        final item = NodeBasePlatform(name: x['name']);
        item.version = x['version'];
        item.path = x['path'];
        item.updateUrl = x['url'];
        final NodeBasePlatformItem node = makeItem(item);
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
    await writeAppFileAsString("/platform.json", JsonEncoder((x) {
      if (x is NodeBasePlatformItem) {
        return {
          "name": x.item.name,
          "version": x.item.version,
          "path": x.item.path,
          "url": x.item.updateUrl
        };
      }
      return null;
    }).convert({
        "platforms": entities
    }));
    setState(() { loading = false; });
  }

  removeItem(NodeBasePlatformItem item) {
    final index = entities.indexOf(item);
    if (index < 0) return;
    setState(() {
      entities.removeAt(index);
    });
  }

  makeItem(NodeBasePlatform item) {
    return NodeBasePlatformItem(item: item, fnRemove: removeItem, fnSaveConfig: saveConfig);
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
        title: Text('Platform Settings'),
        leading: IconButton(
          icon: Icon(Icons.arrow_back),
          onPressed: () { Navigator.pop(context); }
        )
      ),
      body: (entities.length == 0)
        ? Center( child: Text('No platform item.') )
        : ListView.builder(
          padding: const EdgeInsets.all(8),
          itemCount: entities.length,
          itemBuilder: (BuildContext context, int index) {
            return Container( child: entities[index] );
          }), // body
      floatingActionButton: FloatingActionButton(
        tooltip: 'Add Platform',
        child: Icon(Icons.add),
        onPressed: () {
          final item = NodeBasePlatform(name: "");
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
