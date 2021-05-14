import 'package:flutter/material.dart';

class NodeBaseSearch extends SearchDelegate {
  @override
  List<Widget> buildActions(BuildContext context) {
    return <Widget>[
      IconButton(
          icon: Icon(Icons.close),
          onPressed: () {
            if (query == "") {
              Navigator.pop(context);
            } else {
              query = "";
            }
          })
    ];
  }

  @override
  Widget buildLeading(BuildContext context) {
    return IconButton(
        icon: Icon(Icons.arrow_back),
        onPressed: () {
          Navigator.pop(context);
        });
  }

  @override
  Widget buildResults(BuildContext context) {
    return Container(child: Center(child: Text("hello")));
  }

  @override
  Widget buildSuggestions(BuildContext context) {
    List<String> candidateList = ["a", "b", "c"];
    List<String> suggestionList = [];
    query.isEmpty
        ? suggestionList = candidateList
        : suggestionList.addAll(candidateList.where((x) => x.contains(query)));
    return ListView.builder(
        itemCount: suggestionList.length,
        itemBuilder: (context, index) {
          return ListTile(title: Text(suggestionList[index]), onTap: () {});
        });
  }
}
