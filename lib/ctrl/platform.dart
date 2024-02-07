// manage platform like executable files, application files

import 'dart:async';
import 'dart:io';
import 'package:path/path.dart' as path;

import '../util/api.dart';
import '../util/fs.dart';
import '../util/event.dart' as event;

/*
 * == remote ==
 * /nodebase.json -> /workspace/tmp/nodebase.json
 * - version
 * - platform-<os>-<arch>
 * e.g.
 * - version: 1.0
 * - platform-android-arm64: 20240101
 *
 * /app-<os>-<arch>.json -> /workspace/tmp/app-<os>-<arch>.json
 * - name
 * - version
 * /plm-<os>-<arch>.json -> /workspace/tmp/plm-<os>-<arch>.json
 * - name
 * - version
 * /app/<os>/<arch>/<hash>(<name>-<version>).json
 * - url
 * - sha256
 * - description
 * - timestamp
 * - entry
 * /plm/<os>/<arch>/<hash>(<name>-<version>).json
 * - url
 * - sha256
 * - description
 * - timestamp
 * - entry
 *
 * == local ==
 * /workspace/etc/nodebase/app.json
 * - version
 * - sources
 * /workspace/etc/nodebase/sources/plm/<hash>(<name>).list
 * /workspace/etc/nodebase/plm/all.list
 * - name
 * - version
 * - source
 * - meta_json
 * /workspace/etc/nodebase/plm/<hash>(<name>-<version>).json
 * - name
 * - version
 * - manifest (files, directories)
 *    - filename
 *    - type=exe,none
 * /workspace/etc/nodebase/sources/app/<hash>(<name>).list
 * /workspace/etc/nodebase/app/all.list
 * - name
 * - version
 * - source
 * - meta_json
 * /workspace/etc/nodebase/app/<hash>(<name>-<version>).json
 * - name
 * - version
 * - requirements
 *    - platform
 * /workspace/etc/plm/<hash>(<name>-<version>)/...
 * - name
 * - version
 * - source
 * - executable
 *    - filename
 *    - type=exe,none
 * /workspace/etc/app/<hash>(<name>-<version>)/...
 * /workspace/app/...
 * /workspace/plm/...
 */

class _DownloadItem {
  _DownloadItem({
    required this.action,
    required this.cancel,
  });

  final Completer action;
  final StreamController cancel;
}

class Platform {
  Platform({
    required this.baseUrl,
    required this.baseDir,
    required this.os,
    required this.arch});
  String baseUrl;
  String baseDir;
  String os;
  String arch;

  final Map<String, _DownloadItem> _downloadQueue = {};

  String _getEtcBaseDir() => path.join(baseDir, "workspace", "etc");
  String _getTmpBaseDir() => path.join(baseDir, "workspace", "tmp");
  String _getApplicationBaseDir() => path.join(baseDir, "workspace", "app");
  String _getPlatformBaseDir() => path.join(baseDir, "workspace", "plm");

  void changeBaseUrl(String url) { baseUrl = url; }
  Future<bool> isSupported() async {
    final config = await readNodeBaseJson();
    return config.containsKey("platform-$os-$arch");
  }

  Future<Map<String, dynamic>> readNodeBaseJson() async =>
      fsReadFileAsJson(path.join(_getEtcBaseDir(), "nodebase", "nodebase.json"));
  Future<Map<String, dynamic>> readApplicationListJson() async =>
      fsReadFileAsJson(path.join(_getEtcBaseDir(), "nodebase", "app-$os-$arch.json"));
  Future<Map<String, dynamic>> readPlatformListJson() async =>
      fsReadFileAsJson(path.join(_getEtcBaseDir(), "nodebase", "plm-$os-$arch.json"));
  Future<Map<String, dynamic>> readApplicationMetaJson(String name, String version) async =>
      fsReadFileAsJson(path.join(_getEtcBaseDir(), "app", await fsCalcStringHash("$name-$version"), "meta.json"));
  Future<Map<String, dynamic>> readPlatformMetaJson(String name, String version) async =>
      fsReadFileAsJson(path.join(_getEtcBaseDir(), "plm", await fsCalcStringHash("$name-$version"), "meta.json"));

  Future<void> _downloadFile(
      String name,
      String url,
      String targetFilename,
      StreamController? cancel) async {
    final signal = StreamController();
    cancel ??= StreamController();
    await fsGuaranteeDir(targetFilename);
    event.platformToken.add([name, targetFilename, 0]);
    final download = fsProgressDownload(targetFilename, url, signal, cancel);
    signal.stream.listen((message) {
      final progress = message[2];
      if (progress == -1) {
        // cancel or error
        event.platformToken.add([name, targetFilename, -1]);
      } else {
        event.platformToken.add([name, targetFilename, progress]);
      }
    });
    return download;
  }
  Future<void> downloadNodeBaseJson({StreamController? cancel}) async {
    final tmpFilename = path.join(_getTmpBaseDir(), "nodebase.json");
    final doing = _downloadQueue[tmpFilename];
    if (doing != null) return await doing.action.future;
    final action = Completer();
    cancel ??= StreamController();
    _downloadQueue[tmpFilename] = _DownloadItem(action: action, cancel: cancel);
    try {
      await _downloadFile("nodebase.json", "$baseUrl/nodebase.json", tmpFilename, cancel);
      final targetFilename = path.join(_getEtcBaseDir(), "nodebase", "nodebase.json");
      await fsGuaranteeDir(targetFilename);
      final tmpFile = File(tmpFilename);
      await tmpFile.copy(targetFilename);
      await tmpFile.delete();
      action.complete();
    } catch (e) {
      action.completeError(e);
    } finally {
      _downloadQueue.remove(tmpFilename);
    }
  }
  Future<void> downloadApplicationListJson({StreamController? cancel}) async {
    final tmpFilename = path.join(_getTmpBaseDir(), "app-$os-$arch.json");
    final doing = _downloadQueue[tmpFilename];
    if (doing != null) return await doing.action.future;
    final action = Completer();
    cancel ??= StreamController();
    _downloadQueue[tmpFilename] = _DownloadItem(action: action, cancel: cancel);
    try {
      await _downloadFile("app-$os-$arch.json", "$baseUrl/app-$os-$arch.json", tmpFilename, cancel);
      final targetFilename = path.join(_getEtcBaseDir(), "nodebase", "app-$os-$arch.json");
      await fsGuaranteeDir(targetFilename);
      final tmpFile = File(tmpFilename);
      await tmpFile.copy(targetFilename);
      await tmpFile.delete();
      action.complete();
    } catch (e) {
      action.completeError(e);
    } finally {
      _downloadQueue.remove(tmpFilename);
    }
  }
  Future<void> downloadPlatformListJson({StreamController? cancel}) async {
    final tmpFilename = path.join(_getTmpBaseDir(), "plm-$os-$arch.json");
    final doing = _downloadQueue[tmpFilename];
    if (doing != null) return await doing.action.future;
    final action = Completer();
    cancel ??= StreamController();
    _downloadQueue[tmpFilename] = _DownloadItem(action: action, cancel: cancel);
    try {
      await _downloadFile("plm-$os-$arch.json", "$baseUrl/plm-$os-$arch.json", tmpFilename, cancel);
      final targetFilename = path.join(_getEtcBaseDir(), "nodebase", "plm-$os-$arch.json");
      await fsGuaranteeDir(targetFilename);
      final tmpFile = File(tmpFilename);
      await tmpFile.copy(targetFilename);
      await tmpFile.delete();
      action.complete();
    } catch (e) {
      action.completeError(e);
    } finally {
      _downloadQueue.remove(tmpFilename);
    }
  }
  Future<void> downloadApplicationMetaJson(String name, String version, {StreamController? cancel}) async {
    // python3 -c 'from hashlib import sha256; x="node-v20.11.0";print(x, sha256(x.encode("utf-8")).hexdigest())'
    final hash = await fsCalcStringHash("$name-$version");
    final tmpFilename = path.join(_getTmpBaseDir(), "app-$hash-meta.json");
    final doing = _downloadQueue[tmpFilename];
    if (doing != null) return await doing.action.future;
    final action = Completer();
    cancel ??= StreamController();
    _downloadQueue[tmpFilename] = _DownloadItem(action: action, cancel: cancel);
    try {
      await _downloadFile("app-$name-$version.json", "$baseUrl/app/$os/$arch/$hash.json", tmpFilename, cancel);
      final targetFilename = path.join(_getEtcBaseDir(), "app", hash, "meta.json");
      await fsGuaranteeDir(targetFilename);
      final tmpFile = File(tmpFilename);
      await tmpFile.copy(targetFilename);
      await tmpFile.delete();
      action.complete();
    } catch (e) {
      action.completeError(e);
    } finally {
      _downloadQueue.remove(tmpFilename);
    }
  }
  Future<void> downloadPlatformMetaJson(String name, String version, {StreamController? cancel}) async {
    final hash = await fsCalcStringHash("$name-$version");
    final tmpFilename = path.join(_getTmpBaseDir(), "plm-$hash-meta.json");
    final doing = _downloadQueue[tmpFilename];
    if (doing != null) return await doing.action.future;
    final action = Completer();
    cancel ??= StreamController();
    _downloadQueue[tmpFilename] = _DownloadItem(action: action, cancel: cancel);
    try {
      await _downloadFile("plm-$name-$version.json", "$baseUrl/plm/$os/$arch/$hash.json", tmpFilename, cancel);
      final targetFilename = path.join(_getEtcBaseDir(), "plm", hash, "meta.json");
      await fsGuaranteeDir(targetFilename);
      final tmpFile = File(tmpFilename);
      await tmpFile.copy(targetFilename);
      await tmpFile.delete();
      action.complete();
    } catch (e) {
      action.completeError(e);
    } finally {
      _downloadQueue.remove(tmpFilename);
    }
  }
  Future<void> downloadApplicationBinary(String name, String version, String platform, String url, {StreamController? cancel}) async {
    final hash = await fsCalcStringHash("$name-$version");
    final baseName = path.basename(url);
    final tmpFilename = path.join(_getTmpBaseDir(), baseName);
    final doing = _downloadQueue[tmpFilename];
    if (doing != null) return await doing.action.future;
    final action = Completer();
    cancel ??= StreamController();
    _downloadQueue[tmpFilename] = _DownloadItem(action: action, cancel: cancel);
    try {
      await _downloadFile("app-$name-$version.bin", url, tmpFilename, cancel);
      final tmpFile = File(tmpFilename);
      if (baseName.endsWith(".zip")) {
        final targetFilename = path.join(_getApplicationBaseDir(), hash);
        await fsMkdir(targetFilename);
        await fsUnzipFiles(tmpFilename, targetFilename);
      } else {
        final targetFilename = path.join(_getApplicationBaseDir(), hash, baseName);
        await fsGuaranteeDir(targetFilename);
        await tmpFile.copy(targetFilename);
      }
      await tmpFile.delete();
      await _configListAdd("app-list.json", name, "$version:$platform");
    } catch (e) {
      action.completeError(e);
    } finally {
      _downloadQueue.remove(tmpFilename);
    }
  }
  Future<void> downloadPlatformBinary(String name, String version, String url, {StreamController? cancel}) async {
    final hash = await fsCalcStringHash("$name-$version");
    final baseName = path.basename(url);
    final tmpFilename = path.join(_getTmpBaseDir(), baseName);
    final doing = _downloadQueue[tmpFilename];
    if (doing != null) return await doing.action.future;
    final action = Completer();
    cancel ??= StreamController();
    _downloadQueue[tmpFilename] = _DownloadItem(action: action, cancel: cancel);
    try {
      await _downloadFile("plm-$name-$version.bin", url, tmpFilename, cancel);
      final tmpFile = File(tmpFilename);
      if (baseName.endsWith(".zip")) {
        final targetFilename = path.join(_getPlatformBaseDir(), hash);
        await fsMkdir(targetFilename);
        await fsUnzipFiles(tmpFilename, targetFilename);
        for (final fname in await listPlatformExecutableList(name, version)) {
          await NodeBaseApi.apiUtilMarkExecutable(path.join(targetFilename, fname));
        }
      } else {
        final targetFilename = path.join(_getPlatformBaseDir(), hash, baseName);
        await fsGuaranteeDir(targetFilename);
        await tmpFile.copy(targetFilename);
        await NodeBaseApi.apiUtilMarkExecutable(targetFilename);
      }
      await tmpFile.delete();
      await _configListAdd("plm-list.json", name, version);
    } catch (e) {
      action.completeError(e);
    } finally {
      _downloadQueue.remove(tmpFilename);
    }
  }
  Future<void> downloadCancel(String targeFilename) async {
    final doing = _downloadQueue[targeFilename];
    if (doing == null) return;
    doing.cancel.add(true);
    await doing.action.future;
  }

  Future<void> _configListAdd(String target, String name, String version) async {
    final filename = path.join(_getEtcBaseDir(), "nodebase", target);
    final config = await fsReadFileAsJson(filename);
    List<dynamic> list_ = config["items"] ?? [];
    List<String> list = [];
    for (final one in list_) {
      list.add(one.toString());
    }
    final addone = "$name-$version";
    if (!list.contains(addone)) list.add(addone);
    config["items"] = list;
    await fsWriteFileAsJson(filename, config);
  }
  Future<void> _configListRemove(String target, String name, String version) async {
    final filename = path.join(_getEtcBaseDir(), "nodebase", target);
    final config = await fsReadFileAsJson(filename);
    List<dynamic> list_ = config["items"] ?? [];
    List<String> list = [];
    for (final one in list_) {
      list.add(one.toString());
    }
    final addone = "$name-$version";
    int i = list.indexOf(addone);
    if (i >= 0) {
      list.removeAt(i);
    }
    config["items"] = list;
    await fsWriteFileAsJson(filename, config);
  }

  Future<Map<String, List<String>>> listAvailableApplicationList() async {
    // name-version:platform
    final filename = path.join(_getEtcBaseDir(), "nodebase", "app-$os-$arch.json");
    final config = await fsReadFileAsJson(filename);
    Map<String, List<String>> r = {};
    for (final one in config["items"] ?? []) {
      final onestr = one.toString();
      final i = onestr.indexOf('-');
      final name = onestr.substring(0, i);
      final version = onestr.substring(i+1);
      if (r.containsKey(name)) {
        r[name]?.add(version);
      } else {
        r[name] = [version];
      }
    }
    return r;
  }
  Future<Map<String, List<String>>> listInstalledApplicationList() async {
    final filename = path.join(_getEtcBaseDir(), "nodebase", "app-list.json");
    final config = await fsReadFileAsJson(filename);
    Map<String, List<String>> r = {};
    for (final one in config["items"] ?? []) {
      final onestr = one.toString();
      final i = onestr.indexOf('-');
      final name = onestr.substring(0, i);
      final version = onestr.substring(i+1);
      if (r.containsKey(name)) {
        r[name]?.add(version);
      } else {
        r[name] = [version];
      }
    }
    return r;
  }
  Future<Map<String, dynamic>> readApplicationConfig(String name, String version) async {
    final hash = await fsCalcStringHash("$name-$version");
    final filename = path.join(_getEtcBaseDir(), "app", hash, "config.json");
    return await fsReadFileAsJson(filename);
  }
  Future<void> writeApplicationConfig(String name, String version, Map<String, dynamic> json) async {
    final hash = await fsCalcStringHash("$name-$version");
    final filename = path.join(_getEtcBaseDir(), "app", hash, "config.json");
    await fsWriteFileAsJson(filename, json);
  }
  Future<void> removeApplicationBinary(String name, String version, String platform) async {
    final hash = await fsCalcStringHash("$name-$version");
    final dir = Directory(path.join(_getApplicationBaseDir(), hash));
    await _configListRemove("app-list.json", name, "$version:$platform");
    if (!dir.existsSync()) return;
    await dir.delete(recursive: true);
  }

  Future<Map<String, List<String>>> listAvailablePlatformList() async {
    final filename = path.join(_getEtcBaseDir(), "nodebase", "plm-$os-$arch.json");
    final config = await fsReadFileAsJson(filename);
    Map<String, List<String>> r = {};
    for (final one in config["items"] ?? []) {
      final onestr = one.toString();
      final i = onestr.indexOf('-');
      final name = onestr.substring(0, i);
      final version = onestr.substring(i+1);
      if (r.containsKey(name)) {
        r[name]?.add(version);
      } else {
        r[name] = [version];
      }
    }
    return r;
  }
  Future<Map<String, List<String>>> listInstalledPlatformList() async {
    // name-version
    final filename = path.join(_getEtcBaseDir(), "nodebase", "plm-list.json");
    final config = await fsReadFileAsJson(filename);
    Map<String, List<String>> r = {};
    for (final one in config["items"] ?? []) {
      final onestr = one.toString();
      final i = onestr.indexOf('-');
      final name = onestr.substring(0, i);
      final version = onestr.substring(i+1);
      if (r.containsKey(name)) {
        r[name]?.add(version);
      } else {
        r[name] = [version];
      }
    }
    return r;
  }
  Future<List<String>> listPlatformExecutableList(String name, String version) async {
    final meta = await readPlatformMetaJson(name, version);
    List<String> r = [];
    for (final one in meta["executable"] ?? []) {
      r.add(one.toString());
    }
    return r;
  }
  Future<Map<String, dynamic>> readPlatformConfig(String name, String version) async {
    final hash = await fsCalcStringHash("$name-$version");
    final filename = path.join(_getEtcBaseDir(), "plm", hash, "config.json");
    return await fsReadFileAsJson(filename);
  }
  Future<void> writePlatformConfig(String name, String version, Map<String, dynamic> json) async {
    final hash = await fsCalcStringHash("$name-$version");
    final filename = path.join(_getEtcBaseDir(), "plm", hash, "config.json");
    await fsWriteFileAsJson(filename, json);
  }
  Future<void> removePlatformBinary(String name, String version) async {
    final hash = await fsCalcStringHash("$name-$version");
    final dir = Directory(path.join(_getPlatformBaseDir(), hash));
    await _configListRemove("plm-list.json", name, version);
    if (!dir.existsSync()) return;
    // XXX: by default, on windows, MAX_PATh = 260, if too long, will fail
    // ref: https://learn.microsoft.com/en-us/windows/win32/fileio/maximum-file-path-limitation?tabs=registry
    await dir.delete(recursive: true);
  }
}