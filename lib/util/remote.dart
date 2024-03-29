import 'dart:convert';
import 'dart:io';

Future<Map<String, dynamic>?> remoteGet(String url) async {
  HttpClient client = HttpClient();
  try {
    final req = await client.getUrl(Uri.parse(url));
    final response = await req.close();
    if (response.statusCode / 200 == 2) {
      final stream = await response.transform(utf8.decoder).toList();
      final ret = jsonDecode(stream.first) as Map<String, dynamic>;
      return ret;
    }
    throw "request failure";
  } catch (e) {
    return null;
  } finally {
    client.close();
  }
}

Future<Map<String, dynamic>?> remotePost(String url, Map<String, dynamic> data) async {
  HttpClient client = HttpClient();
  try {
    final req = await client.postUrl(Uri.parse(url));
    req.headers.set("Content-Type", "application/json");
    req.write(data);
    final response = await req.close();
    if (response.statusCode / 200 == 2) {
      final stream = await response.transform(utf8.decoder).toList();
      final ret = jsonDecode(stream.first) as Map<String, dynamic>;
      return ret;
    }
    throw "request failure";
  } catch (e) {
    return null;
  } finally {
    client.close();
  }
}