import 'package:path_provider/path_provider.dart';
import 'package:archive/archive.dart';
import 'package:path/path.dart' as path;
import 'dart:io';
import 'dart:developer';

// getApplicationDocumentsDirectory -> /data/data/app/...
// getExternalStorageDirectory      -> /storage/sdcard-external/android/data/app/...

Future<String> get _appPath async {
  final directory = await getApplicationDocumentsDirectory();
  return directory.path;
}

Future<File> fsGetAppFileReference(filepath) async {
  final base = await _appPath;
  return File(path.join(base, filepath));
}

Future<String> fsReadAppFileAsString(filepath) async {
  try {
    final file = await fsGetAppFileReference(filepath);
    String contents = await file.readAsString();
    return contents;
  } catch (e) {
    log("NodeBase [E] fsReadAppFileAsString / ${e.toString()}");
    return "";
  }
}

Future<void> fsWriteAppFileAsString(filepath, contents) async {
  final file = await fsGetAppFileReference(filepath);
  file.writeAsString(contents);
}

Future<Object> fsGetEntity(filepath) async {
  final base = await _appPath;
  final filename = path.join(base, filepath);
  final T = await FileSystemEntity.type(filename);
  if (T == FileSystemEntityType.notFound) {
    return "notFound";
  }
  if (T == FileSystemEntityType.link) {
    return Link(filepath);
  }
  if (T == FileSystemEntityType.file) {
    return File(filepath);
  }
  return Directory(filepath);
}

Future<Directory> fsMkdir(filepath) async {
  final path = await _appPath;
  return await Directory("$path$filepath").create(recursive: true);
}

Future<List<FileSystemEntity>> fsLs(filepath) async {
  final base = await _appPath;
  final filename = path.join(base, filepath);
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

Future<String> fsGetAppBaseDir(String app) async {
  // TODO: check ".." in app
  final base = await _appPath;
  final appBaseDir = path.join(base, "apps", app);
  return appBaseDir;
}

Future<bool> fsRemoveApp(String app) async {
  // TODO: check ".." in app
  if (app == "") return false;
  final base = await _appPath;
  final appBaseDir = path.join(base, "apps", app);
  final dir = Directory(appBaseDir);
  if (await dir.exists()) {
    await dir.delete(recursive: true);
  }
  return true;
}

Future<bool> fsMoveApp(String app, String newname) async {
  // TODO: check ".." in app and newname
  if (app == "" || newname == "" || app == newname) return false;
  final base = await _appPath;
  final appBaseDir = path.join(base, "apps", app);;
  final newBaseDir = path.join(base, "app", newname);;
  final dir = Directory(appBaseDir);
  if (await dir.exists()) {
    await dir.rename(newBaseDir);
  }
  return true;
}

Future<void> fsZipFiles(String zipFilename, List<File> files) async {
  // TODO: try...catch...

  // Create an empty archive
  final archive = Archive();

  for (final file in files) {
    // Read the file bytes
    final bytes = file.readAsBytesSync();

    // Add the file to the archive
    archive.addFile(ArchiveFile(
      path.basename(file.path), // File name
      bytes.length, // File size
      bytes, // File data
    ));
  }

  // Encode the archive to Zip
  final zipData = ZipEncoder().encode(archive);

  // Write the zipped bytes to a file
  File(zipFilename)
    ..createSync(recursive: true) // Create the file if it doesn't exist
    ..writeAsBytesSync(zipData!);
}

Future<void> fsUnzipFiles(String zipFilename, String dstDir) async {
  // TODO: try...catch...
  final bytes = File(zipFilename).readAsBytesSync();

  // Decode the Zip archive
  final archive = ZipDecoder().decodeBytes(bytes);

  for (final file in archive) {
    final filename = file.name;
    if (file.isFile) {
      final data = file.content as List<int>;
      File(path.join(dstDir, filename))
        ..createSync(recursive: true)
        ..writeAsBytesSync(data);
    } else {
      Directory(path.join(dstDir, filename))
        .createSync(recursive: true);
    }
  }
}

Future<void> fsDownload(String filename, String url) async {
  var urlobj = Uri.parse(url);
  var client = HttpClient();
  try {
    final req = await client.getUrl(urlobj);
    final res = await req.close();
    res.pipe(File(filename).openWrite());
  } finally {
    client.close();
  }
}

