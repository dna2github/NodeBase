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
  AppTile? detect;
  bool isInstalled = false;

  bool isDownloading = false;
  bool isRunning = false;
  bool isRemoving = false;

  void setInstall(bool installed) {
    setState(() {
      isInstalled = installed;
    });
  }

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
    if (detect != widget) {
      isInstalled = widget.defaultInstalled;
      detect = widget;
    }
    if (isInstalled) {
      actions.add(IconButton(onPressed: isRemoving ? null : () {
        showInfo(context);
      }, icon: const Icon(Icons.info_outline)));
      actions.add(IconButton(onPressed: isRemoving || isRunning ? null : () {
        // TODO: if platform is not installed, pop up a dialog for installation
        //       list all available name - {versions}
        // TODO: pop up a dialog for exec, env set up
        //       read exec, env from last run or default value
        // TODO: start
      }, icon: const Icon(Icons.play_arrow)));
      actions.add(IconButton(onPressed: isRemoving || isRunning ? null : () {
        // TODO: check if running, no remove
        // TODO: try ... catch ...
        showConfirmDialog(
            context,
            "Remove Application",
            "Do you confirm to remove the application \"${widget.name}-${widget.version}\"?"
        ).then((confirmed) {
          if (!confirmed) return;
          isRemoving = true;
          (() async {
            try {
              await nodebase.instance.platform.removeApplicationBinary(
                  widget.name, widget.version, widget.platform);
              return true;
            } catch (e) {
              log("NodeBase [E] AppTile ... remove application \"${widget.name}-${widget.version}\" $e");
              return false;
            }
          })().then((ok) {
            isRemoving = false;
            if (!ok) return;
            setInstall(false);
            generateSnackBar(context, "Removed application: \"${widget.name}-${widget.version}\"");
          });
        });
      }, icon: const Icon(Icons.delete_forever)));
    } else {
      actions.add(IconButton(onPressed: isDownloading ? null : () {
        (() async {
          final name = widget.name;
          final version = widget.version;
          final confirmed = await showConfirmDialog(
              context,
              "Install Application",
              "Do you confirm to install the application \"${name}-${version}\"?"
          );
          if (!confirmed) return "cancel";
          isDownloading = true;
          final platform = nodebase.instance.platform;
          try {
            await platform.downloadApplicationMetaJson(name, version);
            final json = await platform.readApplicationMetaJson(name, version);
            final url = json["source"];
            await platform.downloadApplicationBinary(name, version, widget.platform, url);
            return "ok";
          } catch (e) {
            log("NodeBase [E] AppTile:install ${widget.name}-${widget.version} ... $e");
            return e.toString();
          }
        })().then((r) {
          isDownloading = false;
          if (r == "cancel") return;
          if (r == "ok") {
            setInstall(true);
            generateSnackBar(context, "Installed application: \"${widget.name}-${widget.version}\"");
          } else {
            generateSnackBar(context, "Failed to install application: \"${widget.name}-${widget.version}\"");
          }
        });
      }, icon: const Icon(Icons.download)));
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