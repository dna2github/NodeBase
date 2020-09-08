# NodeBase
<img src="https://raw.githubusercontent.com/wiki/dna2github/NodeBase/images/log.png" />

Android NodeJS Platform to Build Sharable Application

Running Node.js application over Wifi and share with your friends.

For previous mature version, please explore source code on <a href="https://github.com/dna2github/NodeBase/tree/kotlin">kotlin</a> branch.

Currently we are redesigning whole NodeBase based on Flutter.

## How to use

- Create a new platform, for example named `node`
  - fill node url like `file:///sdcard/bin-node-v10.10.0` or `https://example.com/latest/arm/node`
  - click download button and wait for task complete
  - (NodeBase will copy the binary to its app zone and make it executable)
- Create a new app, for example named `test` and its platform is `node`
  - click into the new app
  - download an app zip into for example `/sdcard/test.zip`
  - fill `Import / Export` text field with `/sdcard/test.zip` 
  - click upload button and wait for task complete
  - (NodeBase will extract zip app as a app folder into app zone)
  - fill `Params` text field (for example, file manager need to config target folder as first param)
  - click `play` button to start node app
  - click `open in browser` button to open the app in a webview
  - click `stop` button to stop node app

### App folder structure

```
/<app_name>/config.json
{
   "host": "http://127.0.0.1"
   "port": 0,
   "home": "/index.html",
   "entry": "index.js"
}

/<app_name>/static/index.html
[...] source code frontend client

/<app_name>/index.js
[...] source code for backend server
[...] hook `/index.html` to load `/app/static/index.html`
```

## Getting Started

This project is a starting point for a Flutter application.

A few resources to get you started if this is your first Flutter project:

- [Lab: Write your first Flutter app](https://flutter.dev/docs/get-started/codelab)
- [Cookbook: Useful Flutter samples](https://flutter.dev/docs/cookbook)

For help getting started with Flutter, view our
[online documentation](https://flutter.dev/docs), which offers tutorials,
samples, guidance on mobile development, and a full API reference.
