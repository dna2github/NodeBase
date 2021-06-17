# NodeBase
<img src="https://raw.githubusercontent.com/wiki/dna2github/NodeBase/images/log.png" />

Android NodeJS Platform to Build Sharable Application

Running Node.js application over Wifi and share with your friends.

For previous mature version, please explore source code on <a href="https://github.com/dna2github/NodeBase/tree/kotlin">kotlin</a> branch.

Currently we are redesigning whole NodeBase based on Flutter.

## How to use

- Platform/App market is online
  - click into platform or application page
  - click on the top-right cart icon button
  - select what you want to download
     - e.g. download platform `node-10.10.0`
     -      download app `file-transfer`
     -      edit app `file-transfer` platform value to `node-10.10.0`
     -      start `file-transfer` by clicking play icon button
- Create a new platform, for example named `node`
  - fill node url like `file:///sdcard/bin-node-v10.10.0` or `https://example.com/latest/arm/node`
  - click download button and wait for task complete
  - (NodeBase will copy the binary to its app zone and make it executable)
  - Wow; now we not only support node binary but also customized exectuables.
- Create a new app, for example named `test` and its platform is `node`
  - click into the new app
  - download an app zip into for example `/sdcard/test.zip`
  - fill `Import / Export` text field with `/sdcard/test.zip` 
  - click upload button and wait for task complete
  - (NodeBase will extract zip app as a app folder into app zone)
  - fill `Params` text field (for example, file manager need to config target folder as first param)
  - click `play` button to start node app
  - click `open in browser` button to open the app in a webview / `pop-out` button to open in external browser
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
ref: https://github.com/stallpool/halfbase/tree/master/nodejs/tinyserver/index.js
[...] source code for backend server
[...] hook `/index.html` to load `/app/static/index.html`
```

App examples: [https://github.com/nodebase0](https://github.com/nodebase0), includes file-viewer-uploader, nodepad, ...


## Development

This project is a starting point for a Flutter application.

A few resources to get you started if this is your first Flutter project:

- [Lab: Write your first Flutter app](https://flutter.dev/docs/get-started/codelab)
- [Cookbook: Useful Flutter samples](https://flutter.dev/docs/cookbook)

For help getting started with Flutter, view our
[online documentation](https://flutter.dev/docs), which offers tutorials,
samples, guidance on mobile development, and a full API reference.

##### NodeJS/Python binary for ARM

ref: https://github.com/dna2github/dna2oslab/releases/tag/0.2.0-android-gt6-arm

##### Java binary

write a shell script `java` and `adb push java /sdcard`
```
#!/system/bin/sh

exec dalvikvm $@
```

create a new platform in NodeBase and download java wrapper from `file:///sdcard/java`

then write a command line tool to have a try. ref: https://github.com/dna2github/dna2sevord/tree/master/past/others/walkserver/javacmd

##### Golang binary

```
# download go source package and extract
cd src
GOOS=android GOARCH=arm64 ./bootstrap.bash

tar zcf go-android-arm64-bootstrap.tar.gz go-android-arm64-bootstrap
adb push go-android-arm64-bootstrap.tar.gz /sdcard/
# we suggest write a javascript script to set up golang environment on your Android
# to extract tar package to NodeBase app zone /data/user/0/net.seven.nodebase/
# e.g. /data/user/0/net.seven.nodebase/go-android-arm64-bootstrap
```

write a shell script `go` and `adb push go /sdcard`

```
#!/system/bin/sh

SELF=$(cd `dirname $0`; pwd)
BASE=/data/user/0/net.seven.nodebase/go-android-arm64-bootstrap
CACHEBASE=${BASE}/cache
mkdir -p ${CACHEBASE}/{cache,tmp,local}
export GOROOT=${BASE}
export GOPATH=${CACHEBASE}/golang/local
export GOCACHE=${CACHEBASE}/golang/cache
export GOTMPDIR=${CACHEBASE}/golang/tmp
export CGO_ENABLED=0
exec ${BASE}/bin/go run $@
```

create a new platform in NodeBase and download go wrapper from `file:///sdcard/go`;

then write a tiny server to have a try. ref: https://github.com/stallpool/halfbase/blob/master/golang/tinyserver/main.go

##### Notice

currently NodeBase support kill a program with 1-level children, for example `go run main.go` will spawn a child process `main`;
if click on `stop` button, NodeBase can kill the `go run` and its child `main`.

if remove `exec` in the `go` wrapper shell script, the shell script will run in `sh`, it spawn `go run` and the `go run` spawn `main`;
when `stop` the application, NodeBase will merely kill `sh` and its child `go run`; but `main` will still be running there,
which may cause next `start` failure (like port has already been used) and need to kill whole NodeBase for cleanup.
