import 'package:flutter/material.dart';
import './util.dart';
import '../util/event.dart' as event;
import '../ctrl/nodebase.dart' as nodebase;

class DownloadTile extends StatefulWidget {
  const DownloadTile({
    super.key,
    required this.name,
    required this.type,
    required this.url,
    required this.filename,
  });

  final String name;
  final String type;
  final String url;
  final String filename;

  @override
  State<StatefulWidget> createState() => _DownloadTileState();
}

class _DownloadTileState extends State<DownloadTile> with TickerProviderStateMixin {
  late AnimationController controller;
  bool isDeterminate = false;
  double progressValue = 0.0;

  void modeDeterminate(double value) => setState(() {
    isDeterminate = true;
    controller.stop();
    controller.value = value;
  });
  void modeNonDeterminate() => setState(() {
    isDeterminate = false;
    controller
      ..forward(from: controller.value)
      ..repeat();
  });

  @override
  void initState() {
    super.initState();
    controller = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 1),
    )..addListener(() {
      setState(() {});
    });
    controller.repeat();
  }

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListTile(
      title: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Text("download"),
          Text(
            "${widget.type} - ${widget.name}",
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SelectableText(
            widget.url,
            maxLines: 1,
          ),
          LinearProgressIndicator(
            value: controller.value,
            semanticsLabel: 'Linear progress indicator',
          ),
        ],
      ),
      trailing: Wrap(
        spacing: 2,
        children: [
          IconButton(onPressed: () {
            nodebase.instance.platform.downloadCancel(widget.filename).then((_) {
              generateSnackBar(
                  context,
                  "Canceled downloading for \"${widget.type} - ${widget.name}\""
              );
            });
          }, icon: const Icon(Icons.file_download_off)),
        ],
      ),
    );
  }
}