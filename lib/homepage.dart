import 'dart:convert';
import 'package:flutter/material.dart';
import './io.dart';
import './search.dart';
import './item_editor.dart';

class NodeBaseHomePage extends StatefulWidget {
  NodeBaseHomePage({Key key, this.title}) : super(key: key);

  final String title;

  @override
  _NodeBaseHomePageState createState() => _NodeBaseHomePageState();
}

class _NodeBaseHomePageState extends State<NodeBaseHomePage> {
  int _counter = 0;
  List<String> entries = <String>[];

  @override
  void initState() {
    super.initState();
    for (var i = 0; i < 100; i ++) entries.add('$i');
    readAppFileAsString("/config.json").then((config) {
      if (config != "") {
         onReady(jsonDecode(config));
      }
    });
    ioLs("/").then((list) {
      entries.clear();
      setState(() {
        for (var i = 0; i < list.length; i++) entries.add(_dirname(list[i].path));
      });
    });
  }
  _dirname(String filepath) {
    return filepath.split("/").last;
  }

  onReady(config) {
    print(config);
    if (config == null) return;
    setState(() { _counter = config['counter']; });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
        actions: <Widget>[
          IconButton(
            onPressed: () { showSearch(context: context, delegate: NodeBaseSearch()); },
            icon: Icon(Icons.search)
          )
        ]
      ),
      body: ListView.separated(
        padding: const EdgeInsets.all(8),
        itemCount: entries.length,
        itemBuilder: (BuildContext context, int index) {
          return Container(
            child: Card(
              child: ListTile(
                title: Text('Card ${entries[index]}'),
                trailing: PopupMenuButton<int>(
                  icon: Icon(Icons.more_vert),
                  onSelected: (int result) {
                    Navigator.push(context, MaterialPageRoute(builder: (context) => NodeBaseItemEditor()));
                  },
                  itemBuilder: (BuildContext context) => <PopupMenuEntry<int>>[
                    const PopupMenuItem<int>( value: 101, child: Text('TODO') )
                  ]
                )
              )
            )
          );
        },
        separatorBuilder: (BuildContext context, int index) => const Divider()
      ),
      floatingActionButton: FloatingActionButton(
        tooltip: 'Add Card',
        child: Icon(Icons.add),
      )
    );
  }
}
