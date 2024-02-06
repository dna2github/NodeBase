import 'package:flutter/material.dart';

SnackBar generateSnackBar(BuildContext context, String content, {
  Duration duration = const Duration(seconds: 2)
}) {
  final snackBar = SnackBar(
    content: Text(content),
    action: SnackBarAction(
        label: "X",
        onPressed: () {
          ScaffoldMessenger.of(context).hideCurrentSnackBar();
        }
    ),
    duration: duration,
  );
  ScaffoldMessenger.of(context).showSnackBar(snackBar);
  return snackBar;
}