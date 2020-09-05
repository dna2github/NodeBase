import 'package:flutter/material.dart';
import './api.dart';

class NodeBasePlatformSettings extends StatefulWidget {
  NodeBasePlatformSettings({Key key}): super(key: key);
  @override
  _NodeBasePlatformSettingsState createState() => _NodeBasePlatformSettingsState();
}

class _NodeBasePlatformSettingsState extends State<NodeBasePlatformSettings> {
  String _batteryLevel = 'Unknown';

  @override
  void initState () {
    _getBatteryLevel();
  }

  Future<void> _getBatteryLevel() async {
    final String batteryLevel = await NodeBaseApi.getBatteryLevel();
    setState(() {
      _batteryLevel = batteryLevel;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Platform Settings'),
        leading: IconButton(
          icon: Icon(Icons.arrow_back),
          onPressed: () { Navigator.pop(context); }
        )
      ),
      body: Center( child: Text('Platform Settings $_batteryLevel') ),
      floatingActionButton: FloatingActionButton(
        tooltip: 'Add Platform',
        child: Icon(Icons.add),
      )
    );
  }
}
