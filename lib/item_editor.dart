import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class NodeBaseItemEditor extends StatefulWidget {
  NodeBaseItemEditor({Key key}): super(key: key);
  @override
  _NodeBaseItemEditorState createState() => _NodeBaseItemEditorState();
}

class _NodeBaseItemEditorState extends State<NodeBaseItemEditor> {
  static const platform = const MethodChannel('samples.flutter.dev/battery');
  String _batteryLevel = 'Unknown battery level.';

  @override
  void initState () {
    _getBatteryLevel();
  }

  Future<void> _getBatteryLevel() async {
    String batteryLevel;
    try {
      final int result = await platform.invokeMethod('getBatteryLevel');
      batteryLevel = 'Battery level at $result % .';
    } on PlatformException catch (e) {
      batteryLevel = "Failed to get battery level: '${e.message}'.";
    }

    setState(() {
      _batteryLevel = batteryLevel;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('ItemEditor'),
        leading: IconButton(
          icon: Icon(Icons.arrow_back),
          onPressed: () { Navigator.pop(context); }
        )
      ),
      body: Center( child: Text('ItemEditor $_batteryLevel') )
    );
  }
}
