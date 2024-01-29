import 'dart:async';

import 'package:flutter/material.dart';
import '../util/event.dart' as event;
import '../ctrl/nodebase.dart' as nodebase;

class SplashPage extends StatefulWidget {
  const SplashPage({super.key});

  @override
  State<StatefulWidget> createState() => _SplashPageState();
}

class _SplashPageState extends State<SplashPage> {
  late StreamSubscription<dynamic> initListener;
  Completer<void> initCompleter = Completer<void>();

  @override
  void initState() {
    super.initState();
    nodebase.instance.initializeApp();
    initListener = event.initializeToken.stream.listen((event) {
      if (!initCompleter.isCompleted) {
        initCompleter.complete();
      }
    });
    splashWait().then((_) {
      // TODO: go to correct page according to current state
      // Navigator.pushAndRemoveUntil(
      //    context,
      //    MaterialPageRoute(builder: (context) => NextPage()),
      //    (Route<dynamic> route) => false,
      // )
    });
    // debug: Future.delayed(const Duration(seconds: 8)).then((_) => event.initializeToken.add(true));
  }

  @override
  void dispose() {
    initListener.cancel();
    super.dispose();
  }

  Future<void> splashWait() async {
    // at least, splash wait for N seconds, N = 3
    // at least, guarantee init complete
    Future<void> delay = Future.delayed(const Duration(seconds: 3));
    await Future.any([initCompleter.future, delay]);
    if (initCompleter.isCompleted) {
      await delay;
    } else {
      await initCompleter.future;
    }
  }

  @override
  Widget build(BuildContext context) {
    // TODO: implement build
    return const Scaffold(
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Image(image: AssetImage('asset/image/logo.png')),
            Text(
                "\nNodeBase",
                style: TextStyle(fontSize: 24, color: Color.fromARGB(128, 0, 0, 0)),
            ),
            Text(
              "Connect the world with friends",
              style: TextStyle(color: Color.fromARGB(65, 0, 0, 0)),
            ),
          ],
        ),
      ),
    );
  }

}