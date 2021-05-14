import 'dart:convert';
import 'package:flutter/material.dart';
import './app_model.dart';
import './io.dart';
import './api.dart';
import './page_app_webview.dart';

class NodeBaseAppHome extends StatefulWidget {
  final NodeBaseApp item;

  NodeBaseAppHome({Key key, this.item}) : super(key: key);
  @override
  _NodeBaseAppHomeState createState() => _NodeBaseAppHomeState();
}

class _NodeBaseAppHomeState extends State<NodeBaseAppHome> {
  bool loading = true;
  bool isRunning = false;
  String wifiIp = "0.0.0.0";
  String appHomeUrl = "";
  String appHomePath = "";
  final ctrlParams = TextEditingController();
  final ctrlDownload = TextEditingController();
  var eventSub = null;

  appStopped() {
    setState(() {
      isRunning = false;
    });
  }

  appStarted() {
    setState(() {
      isRunning = true;
    });
  }

  Future<NodeBasePlatform> loadPlatform(String name) async {
    var config = await readAppFileAsString("/platform.json");
    final List<NodeBasePlatform> list = <NodeBasePlatform>[];
    if (config != "") {
      final data = jsonDecode(config);
      data['platforms'].toList().forEach((x) {
        if (x['name'] != name) return;
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

  Future<NodeBaseAppDetails> loadAppDetails(String name) async {
    var config = await readAppFileAsString("/apps/${name}/config.json");
    if (config != "") {
      final data = jsonDecode(config);
      final item = NodeBaseAppDetails();
      item.host = data['host'];
      item.port = data['port'];
      item.entry = data['entry'];
      item.home = data['home'];
      item.path = await ioGetAppBaseDir(name);
      return item;
    }
    return null;
  }

  @override
  void initState() {
    super.initState();
    NodeBaseApi.fetchWifiIpv4().then((ip) {
      setState(() {
        wifiIp = ip;
      });
      loadAppDetails(widget.item.name).then((item) {
        setState(() {
          if (item.host != "") {
            final parts = item.host.split("://");
            if (parts.length > 1) {
              appHomeUrl = parts[0];
            } else {
              appHomeUrl = 'http';
            }
            appHomeUrl = '${appHomeUrl}://${ip}';
            if (item.port > 0) {
              appHomeUrl = '${appHomeUrl}:${item.port}';
            }
            appHomeUrl = '${appHomeUrl}${item.home}';
          } else {
            appHomeUrl = "";
          }
          appHomePath = item.path;
        });
      });
    });
    if (eventSub == null) {
      eventSub = NodeBaseApi.eventApi.receiveBroadcastStream().listen((m) {
        // <app>\n<state>
        if (m == null) return;
        final parts = m.split("\n");
        if (parts.length < 1) return;
        final appname = parts[0];
        final appstat = parts[1];
        if (appname != widget.item.name) return;
        switch (appstat) {
          case "start":
            {
              appStarted();
            }
            break;
          case "stop":
            {
              appStopped();
            }
            break;
        }
      }, onError: (err) {}, cancelOnError: true);
    }
    NodeBaseApi.appStatus(widget.item.name).then((status) {
      switch (status) {
        case "started":
          {
            appStarted();
          }
          break;
        case "stopped":
        default:
          {
            appStopped();
          }
          break;
      }
      setState(() {
        loading = false;
      });
    });
  }

  @override
  void dispose() {
    ctrlParams.dispose();
    ctrlDownload.dispose();
    eventSub.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (widget.item == null ||
        widget.item.name == null ||
        widget.item.name == "") {
      Navigator.pop(context);
      return null;
    }
    if (loading) {
      return Scaffold(
          body: Center(
              child: CircularProgressIndicator(semanticsLabel: "Loading ...")));
    }
    return Scaffold(
        appBar: AppBar(
            title: Text('Application - ${widget.item.name}'),
            leading: IconButton(
                icon: Icon(Icons.arrow_back),
                onPressed: () {
                  Navigator.pop(context);
                })),
        body: ListView(children: <Widget>[
          const ListTile(title: Text('Basic Info'), dense: true),
          ListTile(title: Text('Platform: ${widget.item.platform}')),
          ListTile(
              title: SelectableText(
                  appHomeUrl == ""
                      ? 'Network: ${wifiIp}'
                      : 'Home: ${appHomeUrl}',
                  maxLines: 1)),
          ListTile(
              title: SelectableText('Location: ${appHomePath}', maxLines: 1)),
          ListTile(
              title: TextField(
                  controller: ctrlParams,
                  decoration: InputDecoration(labelText: 'Params'))),
          ListTile(
              title: Row(children: <Widget>[
            IconButton(
                icon: Icon(Icons.play_arrow),
                onPressed: isRunning
                    ? null
                    : () {
                        setState(() {
                          loading = true;
                        });
                        loadPlatform(widget.item.platform).then((p) {
                          if (p == null || p.path == null || p.path == "") {
                            setState(() {
                              loading = false;
                            });
                            return;
                          }
                          loadAppDetails(widget.item.name).then((info) {
                            if (info == null) {
                              // no config.json
                              return;
                            }
                            final entry = info.entry == null ? "" : info.entry;
                            final cmd =
                                "${p.path} ${info.path}/${entry} ${ctrlParams.text}";
                            NodeBaseApi.appStart(widget.item.name, cmd);
                            setState(() {
                              loading = false;
                            });
                          });
                        });
                      }),
            SizedBox(width: 15),
            IconButton(
                icon: Icon(Icons.stop),
                onPressed: isRunning
                    ? () {
                        NodeBaseApi.appStop(widget.item.name);
                      }
                    : null),
            IconButton(
                icon: Icon(Icons.open_in_browser),
                onPressed: isRunning
                    ? () {
                        // open webview
                        setState(() {
                          loading = true;
                        });
                        loadAppDetails(widget.item.name).then((info) {
                          if (info == null) {
                            // no config.json
                            return;
                          }
                          setState(() {
                            loading = false;
                          });
                          var homeUrl = info.host;
                          if (info.port > 0) homeUrl += ":${info.port}";
                          homeUrl += info.home;
                          Navigator.push(
                              context,
                              MaterialPageRoute(
                                  builder: (context) => NodeBaseAppWebview(
                                      name: widget.item.name, home: homeUrl)));
                        });
                      }
                    : null),
            IconButton(
                icon: Icon(Icons.open_in_new),
                onPressed: (appHomeUrl != "" && isRunning)
                    ? () {
                        NodeBaseApi.appBrowser(appHomeUrl);
                      }
                    : null)
          ])), // Row, ListTile
          const Divider(),
          const ListTile(title: Text('Import/Export'), dense: true),
          ListTile(
              leading: IconButton(
                  icon: Icon(Icons.file_upload),
                  onPressed: () {
                    // TODO: if url, download zip to tmp folder and unpack
                    setState(() {
                      loading = true;
                    });
                    NodeBaseApi.appUnpack(widget.item.name, ctrlDownload.text)
                        .then((ok) {
                      setState(() {
                        loading = false;
                      });
                    });
                  }),
              trailing: IconButton(
                  icon: Icon(Icons.file_download),
                  onPressed: () {
                    setState(() {
                      loading = true;
                    });
                    NodeBaseApi.appPack(widget.item.name, ctrlDownload.text)
                        .then((ok) {
                      setState(() {
                        loading = false;
                      });
                    });
                  }),
              title: TextField(
                  controller: ctrlDownload,
                  decoration: InputDecoration(
                      labelText: 'ZIP file path'))), // Row, ListTile
        ]) // ListView
        );
  }
}
