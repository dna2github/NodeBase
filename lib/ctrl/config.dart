import "dart:convert";
import "dart:io";

import "package:path/path.dart" as path;

import "../util/fs.dart";

/*
 /etc/nodebase/app {
    "sources": []
 }
 */

class Etc {
  Future<Map<String, dynamic>> readConfig(String name, String key) async {
    Map<String, dynamic> r = {};
    try {
      final text = await fsReadAppFileAsString(path.join("etc", name, key));
      r = jsonDecode(text);
    } finally { }
    return r;
  }

  Future<File> writeConfig(String name, String key, Map<String, dynamic> config) async {
    final dir = path.join("etc", name);
    final configName = path.join(dir, key);
    final f = await fsGetAppFileReference(configName);
    try {
      await fsMkdir(dir);
      final text = jsonEncode(config);
      await fsWriteAppFileAsString(configName, text);
    } finally { }
    return f;
  }
}