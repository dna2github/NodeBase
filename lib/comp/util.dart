import 'dart:async';

import 'package:flutter/material.dart';

SnackBar generateSnackBar(BuildContext context, String content, {
  Duration duration = const Duration(seconds: 2)
}) {
  final snackBar = SnackBar(
    content: Text(content),
    action: SnackBarAction(
        label: "X",
        onPressed: () {
          // TODO: To safely refer to a widget's ancestor in its dispose() method,
          //  save a reference to the ancestor by calling dependOnInheritedWidgetOfExactType()
          //  in the widget's didChangeDependencies() method.
          ScaffoldMessenger.of(context).hideCurrentSnackBar();
        }
    ),
    duration: duration,
  );
  ScaffoldMessenger.of(context).showSnackBar(snackBar);
  return snackBar;
}

Future<bool> showConfirmDialog(BuildContext context, String title, String content) async {
  final ok = Completer<bool>();
  final dialog = AlertDialog(
    title: Text(title),
    shape: const BeveledRectangleBorder(),
    content: Text(content),
    actions: [
      TextButton(onPressed: () {
        Navigator.of(context).pop();
        ok.complete(false);
      }, child: const Text("Cancel")),
      ElevatedButton(onPressed: () {
        Navigator.of(context).pop();
        ok.complete(true);
      }, child: const Text("Confirm")),
    ],
  );
  showDialog(context: context, builder: (_) => dialog);
  return ok.future;
}
