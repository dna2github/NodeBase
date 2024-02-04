import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as path;
import 'package:nodebase/util/fs.dart';

void main() {
  test('hash functions (file, string) should work correctly', () async {
    final baseDir = path.dirname(Platform.script.toFilePath());
    final testfile = path.join(baseDir, 'asset', 'image', 'logo.png');
    expect(await fsCalcHash(testfile), "9c3cfce62af42b32b3ad94e40594869065fcf936dff200a7cc042198d218fcec");
    expect(await fsCalcStringHash("logo.png"), "ab211233b6576dbb0f8b5826447eeac61e2a833a99ac5d788fbc1a174c3c6ce5");
  });
}
