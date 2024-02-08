import 'dart:async';

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
  bool isCanceling = false;

  late StreamSubscription progress;

  void modeDeterminate(double value) => setState(() {
    isDeterminate = true;
    controller.stop();
    controller.value = value;
  });
  void modeNonDeterminate() => setState(() {
    isDeterminate = false;
    controller
      ..forward(from: 0)
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

    progress = event.platformToken.stream.listen((msg) {
      final T = msg[0];
      if (T != "download") return;
      final F = msg[3];
      if (F != widget.filename) return;
      final V = msg[4];
      if (V == -99) {
        modeNonDeterminate();
      } else if (V == -1) {
        setState(() {
          isCanceling = true;
        });
      } else {
        modeDeterminate(V + 0.0);
      }
    });
  }

  @override
  void dispose() {
    progress.cancel();
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
          IconButton(onPressed: isCanceling ? null :() {
            (() async {
              final confirmed = await showConfirmDialog(
                  context,
                  "Cancel Downloading",
                  "Do you confirm to cancel downloading for \"${widget.type} - ${widget.name}\"?"
              );
              if (!confirmed) return false;
              isCanceling = true;
              await nodebase.instance.platform.downloadCancel(widget.filename);
              return true;
            })().then((ok) {
              isCanceling = false;
              if (!ok) return;
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