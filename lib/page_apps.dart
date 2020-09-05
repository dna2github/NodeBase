import 'package:flutter/material.dart';
import './api.dart';

class NodeBaseApplications extends StatefulWidget {
  NodeBaseApplications({Key key}): super(key: key);
  @override
  _NodeBaseApplicationsState createState() => _NodeBaseApplicationsState();
}

class _NodeBaseApplicationsState extends State<NodeBaseApplications> {
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
        title: Text('Applications'),
        leading: IconButton(
          icon: Icon(Icons.arrow_back),
          onPressed: () { Navigator.pop(context); }
        )
      ),
      body: Center( child: Text('Applications $_batteryLevel') ),
      floatingActionButton: FloatingActionButton(
        tooltip: 'Add Application',
        child: Icon(Icons.add),
      )
    );
  }
}
