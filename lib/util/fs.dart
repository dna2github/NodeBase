import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:developer';

import 'package:archive/archive.dart';
import 'package:crypto/crypto.dart';
import 'package:path/path.dart' as path;

import './api.dart';

// getApplicationDocumentsDirectory -> /data/data/app/...
// getExternalStorageDirectory      -> /storage/sdcard-external/android/data/app/...

var _appPath_ = "";
Future<String> get _appPath async {
  if (_appPath_ != "") return _appPath_;
  _appPath_ = await NodeBaseApi.apiUtilGetWorkspacePath();
  log("NodeBase [I] app path: $_appPath_");
  return _appPath_;
}

Future<String> fsGetBaseDir() async {
  return await _appPath;
}

Future<String> fsReadFileAsString(filepath) async {
  try {
    final baseDir = await _appPath;
    if (!filepath.startsWith(baseDir)) return "";
    final file = File(filepath);
    String contents = await file.readAsString();
    return contents;
  } catch (e) {
    log("NodeBase [E] fsReadAppFileAsString ... ${e.toString()}");
    return "";
  }
}

Future<void> fsWriteFileAsString(filepath, contents) async {
  final baseDir = await _appPath;
  if (!filepath.startsWith(baseDir)) return;
  final file = File(filepath);
  file.writeAsString(contents);
}

Future<Map<String, dynamic>> fsReadFileAsJson(String filepath) async {
  Map<String, dynamic> r = {};
  try {
    final baseDir = await _appPath;
    if (!filepath.startsWith(baseDir)) return r;
    final text = await fsReadFileAsString(filepath);
    r = jsonDecode(text);
  } catch(e) {
    log("NodeBase [E] fsReadFileAsString ... $e");
  }
  return r;
}

Future<File> fsWriteFileAsJson(String filepath, Map<String, dynamic> config) async {
  final f = File(filepath);
  try {
    await fsGuaranteeDir(f.path);
    final text = jsonEncode(config);
    await fsWriteFileAsString(filepath, text);
  } catch(e) {
    log("NodeBase [E] fsWriteFileAsString ... $e");
  }
  return f;
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
  final base = await _appPath;
  return await Directory(path.join(base, filepath)).create(recursive: true);
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
    for (var entity in entities) {
      list.add(entity);
    }
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
  final appBaseDir = path.join(base, "apps", app);
  final newBaseDir = path.join(base, "app", newname);
  final dir = Directory(appBaseDir);
  if (await dir.exists()) {
    await dir.rename(newBaseDir);
  }
  return true;
}

Future<void> fsZipFiles(String zipFilename, List<File> files) async {
  try {
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
  } catch(e) {
    log("NodeBase [E] fsZipFiles ... ${e.toString()}");
  }
}

Future<void> fsUnzipFiles(String zipFilename, String dstDir) async {
  try {
    final bytes = File(zipFilename).readAsBytesSync();
    // Decode the Zip archive
    final archive = ZipDecoder().decodeBytes(bytes);
    await fsUnzip(archive, dstDir);
  } catch (e) {
    log("NodeBase [E] fsUnzipFiles ... ${e.toString()}");
  }
}

Future<void> fsUnzipTarFiles(String tarFilename, String dstDir) async {
  try {
    final bytes = File(tarFilename).readAsBytesSync();
    // Decode the Zip archive
    final archive = TarDecoder().decodeBytes(bytes);
    await fsUnzip(archive, dstDir);
  } catch (e) {
    log("NodeBase [E] fsUnzipFiles ... ${e.toString()}");
  }
}

Future<void> fsUnzipXzTarFiles(String xztarFilename, String dstDir) async {
  try {
    final bytes = File(xztarFilename).readAsBytesSync();
    // Decode the Zip archive
    final archive = TarDecoder().decodeBytes(XZDecoder().decodeBytes(bytes));
    await fsUnzip(archive, dstDir);
  } catch (e) {
    log("NodeBase [E] fsUnzipFiles ... ${e.toString()}");
  }
}

Future<void> fsUnzipGzTarFiles(String gztarFilename, String dstDir) async {
  try {
    final bytes = File(gztarFilename).readAsBytesSync();
    // Decode the Zip archive
    final archive = TarDecoder().decodeBytes(GZipDecoder().decodeBytes(bytes));
    await fsUnzip(archive, dstDir);
  } catch (e) {
    log("NodeBase [E] fsUnzipFiles ... ${e.toString()}");
  }
}

Future<void> fsUnzipBzTarFiles(String bztarFilename, String dstDir) async {
  try {
    final bytes = File(bztarFilename).readAsBytesSync();
    // Decode the Zip archive
    final archive = TarDecoder().decodeBytes(BZip2Decoder().decodeBytes(bytes));
    await fsUnzip(archive, dstDir);
  } catch (e) {
    log("NodeBase [E] fsUnzipFiles ... ${e.toString()}");
  }
}

Future<void> fsUnzip(Archive archive, String dstDir) async {
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

Future<void> fsGuaranteeDir(String filename) async {
  final dir = Directory(path.dirname(filename));
  if (!dir.existsSync()) {
    await dir.create(recursive: true);
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

Future<void> fsProgressDownload(
    String filename, String url,
    StreamController progressToken,
    StreamController cancelToken) async {
  // ref: filled by GPT 4 turbo and optimized
  final httpClient = HttpClient();
  final done = Completer();
  bool canCancel = false;
  try {
    // Parse the URL
    final uri = Uri.parse(url);
    // Open a request
    final request = await httpClient.getUrl(uri);
    // Send the request
    final response = await request.close();

    // Check if the response is OK (status code 200)
    if (response.statusCode == 200) {
      // Get the total length of the file
      final contentLength = response.contentLength;
      int downloadedLength = 0;

      // Create a new file (overwrite if exists)
      final file = File(filename);
      final fileSink = file.openWrite();

      // Listen for response data
      final subscription = response.listen(
            (List<int> chunk) {
          // Update the downloaded length
          downloadedLength += chunk.length;
          // Write the chunk to file
          fileSink.add(chunk);

          // Calculate and print the download progress
          double progressRate = contentLength == 0 ? 1 : (downloadedLength / contentLength);
          progressToken.add([downloadedLength, contentLength, progressRate]);
          log('NodeBase [D] fsProgressDownload ... progress ${(progressRate * 100).toStringAsFixed(2)}%');
        },
        onDone: () async {
          // Close the fileSink to ensure all bytes are written
          await fileSink.close();
          progressToken.add([-1, contentLength, 1]);
          log('NodeBase [D] fsProgressDownload ... complete $filename');
          done.complete();
        },
        onError: (e) {
          progressToken.add([-1, contentLength, -1]);
          log('NodeBase [E] fsProgressDownload ... $e');
          done.complete('error');
        },
        cancelOnError: true,
      );

      canCancel = true;
      cancelToken.stream.listen((_) {
        progressToken.add([-1, contentLength, -1]);
        subscription.cancel();
        fileSink.close();
        done.complete('cancel');
        httpClient.close();
      });
    } else {
      log('NodeBase [E] fsProgressDownload ... http ${response.statusCode}');
      done.complete('error');
    }
  } catch (e) {
    log('NodeBase [E] fsProgressDownload ... $e - $url');
    done.complete('error');
  } finally {
    await done.future;
    httpClient.close();
    await progressToken.close();
    // if no stream listener, it will hang there
    if (canCancel) await cancelToken.close();
  }
}

Future<String> fsCalcHash(String filename) async {
  final f = File(filename);
  if (!f.existsSync()) return "";
  final digest = await sha256.bind(f.openRead()).first;
  return digest.toString();
}

Future<String> fsCalcStringHash(String text) async {
  final digest = sha256.convert(utf8.encode(text));
  return digest.toString();
}
