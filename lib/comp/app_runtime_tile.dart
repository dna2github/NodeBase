import 'package:flutter/material.dart';
import './util.dart';
import '../ctrl/application_def.dart';

class AppRuntimeTile extends StatefulWidget {
  const AppRuntimeTile({super.key, required this.process});

  final IApplicationProcess process;

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
      title: Text(widget.process.getName()),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text("platform: ${widget.process.getPlatform()}"),
        ],
      ),
      trailing: Wrap(
        spacing: 2,
        children: [
          IconButton(onPressed: () {
            showInfo(context);
          }, icon: const Icon(Icons.info_outline)),
          IconButton(onPressed: () {
            showConfirmDialog(
                context,
                "Stop Application",
                "Do you confirm to stop the application \"${widget.process.getName()}\"?"
            ).then((confirmed) {
              if (!confirmed) return;
              widget.process.stop().then((_) {
                generateSnackBar(
                    context,
                    "Stopped application \"${widget.process.getName()}\""
                );
              });
            });
          }, icon: const Icon(Icons.stop)),
        ],
      ),
      tileColor: const Color.fromARGB(255, 230, 255, 230),
    );
  }

  void showInfo(BuildContext context) {
    final cmd = widget.process.getCmd();
    final cmdMain = cmd[0];
    final cmdEntry = cmd.length > 1 ? "\n${cmd[1]}" : "";
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
              SelectableText(widget.process.getEnv().entries.map(
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
}