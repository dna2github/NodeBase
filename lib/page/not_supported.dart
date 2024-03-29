import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../ctrl/nodebase.dart' as nodebase;

class NotSupportedPage extends StatefulWidget {
  const NotSupportedPage({super.key});

  @override
  State<StatefulWidget> createState() => _NotSupportedPageState();
}

class _NotSupportedPageState extends State<NotSupportedPage> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            const Image(image: AssetImage('asset/image/logo.png')),
            const Text(
              "\nNodeBase",
              style: TextStyle(fontSize: 24, color: Color.fromARGB(128, 0, 0, 0)),
            ),
            Row(
              children: <Widget>[
                const Spacer(),
                Row(
                  children: [
                    Column(
                      children: [
                        const Text(
                          "Sorry, we are not yet have",
                          style: TextStyle(color: Color.fromARGB(128, 0, 0, 0)),
                        ),
                        Row(
                          children: [
                            const Text(
                              "support for ",
                              style: TextStyle(color: Color.fromARGB(128, 0, 0, 0)),
                            ),
                            Text(
                              "\"${nodebase.instance.platform.getName()}\"",
                              style: const TextStyle(color: Color.fromARGB(255, 255, 128, 128)),
                            ),
                          ],
                        ),
                        const Text(" "),
                      ],
                    ),
                  ]
                ),
                const Spacer(),
              ],
            ),
            ElevatedButton(onPressed: () => {
              if (Platform.isAndroid) {
                SystemChannels.platform.invokeMethod('SystemNavigator.pop')
              } else {
                exit(0)
              }
            }, child: const Text("Exit"))
          ],
        ),
      ),
    );
  }
}