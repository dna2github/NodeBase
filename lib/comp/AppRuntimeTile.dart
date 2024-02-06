import 'dart:async';

import 'package:flutter/material.dart';
import './util.dart';
import '../ctrl/application.dart';
import '../util/event.dart' as event;
import '../ctrl/nodebase.dart' as nodebase;

class AppRuntimeTile extends StatefulWidget {
  const AppRuntimeTile({super.key, required this.process});

  final ApplicationProcess process;

  @override
  State<StatefulWidget> createState() => _AppRuntimeTileState();
}

class _AppRuntimeTileState extends State<AppRuntimeTile> {

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
    return ListTile(
      title: Text(widget.process.name),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text("platform: ${widget.process.platform}"),
        ],
      ),
      trailing: Wrap(
        spacing: 2,
        children: [
          IconButton(onPressed: () {
            showInfo(context);
          }, icon: const Icon(Icons.info_outline)),
          IconButton(onPressed: () {
            confirmStop(context).then((confirmed) {
              if (!confirmed) return;
              widget.process.stop().then((_) {
                generateSnackBar(
                    context,
                    "Stopped application \"${widget.process.name}\""
                );
              });
            });
          }, icon: const Icon(Icons.stop)),
        ],
      ),
      tileColor: Color.fromARGB(255, 230, 255, 230),
    );
  }

  void showInfo(BuildContext context) {
    final cmdMain = widget.process.cmd[0];
    final cmdEntry = widget.process.cmd.length > 1 ? "\n${widget.process.cmd[1]}" : "";
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
              const Text("-- CMD -- "),
              SelectableText("$cmdMain$cmdEntry"),
              const Divider(),
              const Text("-- ENV --"),
              SelectableText(widget.process.env.entries.map(
                      (kv) => "${kv.key} =\n${kv.value}"
              ).toList().join("\n\n")),
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

  Future<bool> confirmStop(BuildContext context) async {
    final ok = Completer<bool>();
    final dialog = AlertDialog(
      title: const Text("Stop Application"),
      shape: BeveledRectangleBorder(),
      content: Text("Do you confirm to stop the application \"${widget.process.name}\""),
      actions: [
        TextButton(onPressed: () {
          Navigator.of(context).pop();
          ok.complete(false);
        }, child: const Text("Cancel")),
        ElevatedButton(onPressed: () {
          Navigator.of(context).pop();
          ok.complete(true);
        }, child: const Text("Stop")),
      ],
    );
    showDialog(context: context, builder: (_) => dialog);
    return ok.future;
  }
}