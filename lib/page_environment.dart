import 'package:flutter/material.dart';
import './api.dart';

class NodeBaseEnvironmentSettings extends StatefulWidget {
  NodeBaseEnvironmentSettings({Key key}) : super(key: key);
  @override
  _NodeBaseEnvironmentSettingsState createState() =>
      _NodeBaseEnvironmentSettingsState();
}

class _NodeBaseEnvironmentSettingsState
    extends State<NodeBaseEnvironmentSettings> {
  String _batteryLevel = 'Unknown';

  @override
  void initState() {
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
            title: Text('Environment Settings'),
            leading: IconButton(
                icon: Icon(Icons.arrow_back),
                onPressed: () {
                  Navigator.pop(context);
                })),
        body: Center(child: Text('Environment Settings $_batteryLevel')));
  }
}
