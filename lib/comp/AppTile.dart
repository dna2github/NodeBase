import 'dart:developer';

import 'package:flutter/material.dart';
import './util.dart';
import '../ctrl/nodebase.dart' as nodebase;

class AppTile extends StatefulWidget {
  const AppTile({
    super.key,
    required this.name,
    required this.version,
    required this.platform,
    this.defaultInstalled = false,
  });

  final String name;
  final String version;
  final String platform;
  final bool defaultInstalled;

  @override
  State<StatefulWidget> createState() => _AppTileState();
}

class _AppTileState extends State<AppTile> {
  bool isInstalled = false;

  @override
  void initState() {
    super.initState();
    isInstalled = widget.defaultInstalled;
  }

  @override
  void dispose() {
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final List<Widget> actions = [];
    if (!isInstalled) {
      actions.add(IconButton(onPressed: () {
        (() async {
          final name = widget.name;
          final version = widget.version;
          final confirmed = await showConfirmDialog(
              context,
              "Install Application",
              "Do you confirm to install the application \"${name}-${version}\"?"
          );
          if (!confirmed) return "cancel";
          final platform = nodebase.instance.platform;
          try {
            await platform.downloadApplicationMetaJson(name, version);
            final json = await platform.readPlatformMetaJson(name, version);
            final url = json["source"];
            await platform.downloadApplicationBinary(name, version, url);
            return "ok";
          } catch (e) {
            log("NodeBase [E] AppTile:install ${widget.name}-${widget.version} ... $e");
            return e.toString();
          }
        })().then((r) {
          if (r == "cancel") return;
          if (r == "ok") {
            generateSnackBar(context, "Installed application: \"${widget.name}-${widget.version}\"");
          } else {
            generateSnackBar(context, "Failed to install application: \"${widget.name}-${widget.version}\"");
          }
        });
      }, icon: const Icon(Icons.download)));
    }
    if (isInstalled) {
      actions.add(IconButton(onPressed: () {
        showInfo(context);
      }, icon: const Icon(Icons.info_outline)));
      actions.add(IconButton(onPressed: () {
        // TODO: if platform is not installed, pop up a dialog for installation
        //       list all available name - {versions}
        // TODO: pop up a dialog for exec, env set up
        //       read exec, env from last run or default value
        // TODO: start
      }, icon: const Icon(Icons.play_arrow)));
      actions.add(IconButton(onPressed: () {
        showConfirmDialog(
            context,
            "Remove Application",
            "Do you confirm to remove the application \"${widget.name}-${widget.version}\"?"
        ).then((confirmed) {
          if (!confirmed) return;
          nodebase.instance.platform.removeApplicationBinary(widget.name, widget.version).then((_) {
            generateSnackBar(context, "Removed application: \"${widget.name}-${widget.version}\"");
          });
        });
      }, icon: const Icon(Icons.delete_forever)));
    }
    return ListTile(
      title: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            "${widget.name}-${widget.version}",
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text("platform: ${widget.platform}")
        ],
      ),
      trailing: Wrap(
        spacing: 2,
        children: actions,
      ),
    );
  }

  void showInfo(BuildContext context) {
    final name = widget.name;
    final version = widget.version;
    showDialog(context: context, builder: (_) => AlertDialog(
      title: const Text("Application Info"),
      shape: const BeveledRectangleBorder(),
      content: SizedBox(
        height: 200,
        width: 300,
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text("-- Basic -- "),
              SelectableText("$name-$version\n(${widget.platform})"),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text("OK")),
      ],
    ));
  }
}