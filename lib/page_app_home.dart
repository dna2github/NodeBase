import 'dart:convert';
import 'package:flutter/material.dart';
import './app_model.dart';
import './io.dart';
import './api.dart';
import './page_app_webview.dart';

class NodeBaseAppHome extends StatefulWidget {
  final NodeBaseApp item;

  NodeBaseAppHome({Key key, this.item}): super(key: key);
  @override
  _NodeBaseAppHomeState createState() => _NodeBaseAppHomeState();
}

class _NodeBaseAppHomeState extends State<NodeBaseAppHome> {

  bool isRunning = false;
  String wifiIp = "0.0.0.0";
  final ctrlParams = TextEditingController();
  var eventSub = null;

  appStopped() {
    setState(() { isRunning = false; });
  }

  appStarted() {
    setState(() { isRunning = true; });
  }

  Future<NodeBasePlatform> loadPlatform(String name) async {
    var config = await readAppFileAsString("/platform.json");
    final List<NodeBasePlatform> list = <NodeBasePlatform>[];
    if (config != "") {
      final data = jsonDecode(config);
      data['platforms'].toList().forEach((x) {
        final item = NodeBasePlatform(name: x['name']);
        item.path = x['path'];
        item.updateUrl = x['url'];
        list.add(item);
      });
      if (list.length <= 0) return null;
      return list[0];
    }
    return null;
  }


  @override
  void initState () {
    super.initState();
    NodeBaseApi.fetchWifiIpv4().then((ip) {
      setState(() { wifiIp = ip; });
    });
    if (eventSub == null) {
      eventSub = NodeBaseApi.eventApi.receiveBroadcastStream().listen(
        (m) {
          // <app>\n<state>
          if (m == null) return;
          final parts = m.split("\n");
          if (parts.length < 1) return;
          final appname = parts[0];
          final appstat = parts[1];
          if (appname != widget.item.name) return;
          switch (appstat) {
            case "start": {
              appStarted();
            } break;
            case "stop": {
              appStopped();
            } break;
          }
        },
        onError: (err) {},
        cancelOnError: true
      );
    }
    NodeBaseApi.appStatus(widget.item.name).then((status) {
      switch(status) {
        case "started": {
          appStarted();
        } break;
        case "stopped":
        default: {
          appStopped();
        } break;
      }
    });
  }

  @override
  void dispose () {
    ctrlParams.dispose();
    eventSub.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (widget.item == null || widget.item.name == null || widget.item.name == "") {
      Navigator.pop(context);
      return null;
    }
    return Scaffold(
      appBar: AppBar(
        title: Text('Application - ${widget.item.name}'),
        leading: IconButton(
          icon: Icon(Icons.arrow_back),
          onPressed: () { Navigator.pop(context); }
        )
      ),
      body: ListView(
        children: <Widget>[
          ListTile( title: Text('Network: ${wifiIp}') ),
          ListTile( title: Text('Platform: ${widget.item.platform}') ),
          ListTile( title: TextField(
            controller: ctrlParams,
            decoration: InputDecoration( labelText: 'Params' )
          ) ),
          ListTile( title: Row(
            children: <Widget>[
              IconButton(
                icon: Icon(Icons.play_arrow),
                onPressed: isRunning ? null : () {
                  setState(() { isRunning = true; });
                  loadPlatform(widget.item.platform).then((p) {
                    if (p == null || p.path == null || p.path == "") {
                      setState(() { isRunning = false; });
                      return;
                    }
                    final cmd = "${p.path} ${ctrlParams.text}";
                    NodeBaseApi.appStart(widget.item.name, cmd);
                  });
                }
              ),
              SizedBox( width: 15 ),
              IconButton(
                icon: Icon(Icons.stop),
                onPressed: isRunning ? () {
                  NodeBaseApi.appStop(widget.item.name);
                } : null
              ),
              IconButton(
                icon: Icon(Icons.open_in_browser),
                onPressed: isRunning == isRunning ? () {
                  // open webview?
                  Navigator.push(context, MaterialPageRoute(
                    builder: (context) => NodeBaseAppWebview()
                  ) );
                } : null
              )
            ]
          ) ) // Row, ListTile
        ]
      ) // ListView
    );
  }
}
