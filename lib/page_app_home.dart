import 'package:flutter/material.dart';
import './api.dart';

class NodeBaseAppHome extends StatefulWidget {
  NodeBaseAppHome({Key key}): super(key: key);
  @override
  _NodeBaseAppHomeState createState() => _NodeBaseAppHomeState();
}

class _NodeBaseAppHomeState extends State<NodeBaseAppHome> {

  @override
  void initState () {
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Application'),
        leading: IconButton(
          icon: Icon(Icons.arrow_back),
          onPressed: () { Navigator.pop(context); }
        )
      ),
      body: Center( child: Text('AppHome') )
    );
  }
}
