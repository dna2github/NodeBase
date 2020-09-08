import 'dart:io';
import 'package:path_provider/path_provider.dart';

// getApplicationDocumentsDirectory -> /data/data/app/...
// getExternalStorageDirectory      -> /storage/sdcard-external/android/data/app/...

Future<String> get _appPath async {
  final directory = await getApplicationDocumentsDirectory();
  return directory.path;
}

Future<File> getAppFileReference(filepath) async {
  final path = await _appPath;
  return File('$path$filepath');
}

Future<String> readAppFileAsString(filepath) async {
  try {
    final file = await getAppFileReference(filepath);
    String contents = await file.readAsString();
    return contents;
  } catch (e) {
    return "";
  }
}

Future<File> writeAppFileAsString(filepath, contents) async {
  final file = await getAppFileReference(filepath);
  file.writeAsString(contents);
}

Future<FileSystemEntity> ioGetEntity(filepath) async {
  final path = await _appPath;
  final filename = '$path$filepath';
  final T = await FileSystemEntity.type(filename);
  if (T == FileSystemEntityType.notFound) {
    return null;
  }
  if (T == FileSystemEntityType.link) {
    return Link(filepath);
  }
  if (T == FileSystemEntityType.file) {
    return File(filepath);
  }
  return Directory(filepath);
}

Future<Directory> ioMkdir(filepath) async {
  final path = await _appPath;
  return await Directory('$path$filepath').create(recursive: true);
}

Future<List<FileSystemEntity>> ioLs(filepath) async {
  final path = await _appPath;
  final filename = '$path$filepath';
  final list = <FileSystemEntity>[];
  final T = await FileSystemEntity.type(filename);
  if (T == FileSystemEntityType.notFound) {
  } else if (T == FileSystemEntityType.link) {
    // ignore files under link directory
    list.add(Link(filename));
  } else if (T == FileSystemEntityType.file) {
    list.add(File(filename));
  } else {
    final dir = Directory(filename);
    final entities = await dir.list(recursive: false, followLinks: false).toList();
    entities.forEach((FileSystemEntity entity) {
      list.add(entity);
    });
  }
  return list;
}

Future<String> ioGetAppBaseDir(String app) async {
  final path = await _appPath;
  final appBaseDir = '${path}/apps/${app}';
  return appBaseDir;
}

Future<bool> ioRemoveApp(String app) async {
  final path = await _appPath;
  final appBaseDir = '${path}/apps/${app}';
  final dir = Directory(appBaseDir);
  if (await dir.exists()) {
    await dir.delete(recursive: true);
  }
  return true;
}

Future<bool> ioMoveApp(String app, String newname) async {
  final path = await _appPath;
  final appBaseDir = '${path}/apps/${app}';
  final newBaseDir = '${path}/apps/${newname}';
  final dir = Directory(appBaseDir);
  if (await dir.exists()) {
    await dir.rename(newBaseDir);
  }
  return true;
}
