import 'dart:developer';

import 'package:flutter/material.dart';
import './util.dart';
import '../ctrl/nodebase.dart' as nodebase;

class PlatformTile extends StatefulWidget {
  const PlatformTile({
    super.key,
    required this.name,
    required this.version,
    this.defaultInstalled = false,
    this.defaultRunning = false,
    this.userDefined = false,
  });

  final String name;
  final String version;
  final bool defaultInstalled;
  final bool defaultRunning;
  final bool userDefined;

  @override
  State<StatefulWidget> createState() => _AppTileState();
}

class _AppTileState extends State<PlatformTile> {
  PlatformTile? detect;
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
      isRunning = widget.defaultRunning;
      detect = widget;
    }
    if (isInstalled) {
      actions.add(IconButton(onPressed: isRemoving ? null : () {
        showInfo(context);
      }, icon: const Icon(Icons.info_outline)));
      actions.add(IconButton(onPressed: isRemoving || isRunning ? null : () {
        // TODO: check if running, no remove
        // TODO: try ... catch ...
        showConfirmDialog(
            context,
            "Remove Platform",
            "Do you confirm to remove the platform \"${widget.name}-${widget.version}\"?"
        ).then((confirmed) {
          if (!confirmed) return;
          isRemoving = true;
          (() async {
            try {
              await nodebase.instance.platform.removePlatformBinary(widget.name, widget.version);
              return true;
            } catch(e) {
              log("NodeBase [E] PlatformTile ... remove platform \"${widget.name}-${widget.version}\" $e");
              return false;
            }
          })().then((ok) {
            isRemoving = false;
            if (!ok) return;
            setInstall(false);
            generateSnackBar(context, "Removed platform: \"${widget.name}-${widget.version}\"");
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
              "Install Platform",
              "Do you confirm to install the platform \"$name-$version\"?"
          );
          if (!confirmed) return "cancel";
          isDownloading = true;
          final platform = nodebase.instance.platform;
          try {
            await platform.downloadPlatformMetaJson(name, version);
            final json = await platform.readPlatformMetaJson(name, version);
            final url = json["source"];
            await platform.downloadPlatformBinary(name, version, url);
            return "ok";
          } catch (e) {
            log("NodeBase [E] PlatformTile:install ${widget.name}-${widget.version} ... $e");
            return e.toString();
          }
        })().then((r) {
          isDownloading = false;
          if (r == "cancel") return;
          if (r == "ok") {
            setInstall(true);
            generateSnackBar(context, "Installed platform: \"${widget.name}-${widget.version}\"");
          } else {
            generateSnackBar(context, "Failed to install platform: \"${widget.name}-${widget.version}\"");
          }
        });
      }, icon: const Icon(Icons.download)));
    }
    return ListTile(
      title: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              ...(widget.userDefined ? [
                const Tooltip(
                  message: "Not in marketplace",
                  child: Icon(Icons.person_pin_circle_rounded, size: 12),
                ),
              ]: []),
              Expanded(child: Text(
                "${widget.name}-${widget.version}",
                overflow: TextOverflow.ellipsis,
              )),
            ],
          )
        ],
      ),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text("(${nodebase.instance.platform.getName()})")
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
      title: const Text("Platform Info"),
      shape: const BeveledRectangleBorder(),
      content: SizedBox(
        height: 200,
        width: 300,
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text("-- Basic -- "),
              SelectableText("$name-$version\n(${nodebase.instance.platform.getName()})"),
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