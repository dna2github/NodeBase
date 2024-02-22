# nodebase

<img src="https://raw.githubusercontent.com/wiki/dna2github/NodeBase/images/log.png" />

Running Node.js application over Wifi and share with your friends.

For previous mature version, please explore source code on
 <a href="https://github.com/dna2github/NodeBase/tree/kotlin">kotlin</a> branch and
 <a href="https://github.com/dna2github/NodeBase/tree/v0flutter">v0flutter</a> branch.

## Rewrite Progress

- [x] migrate kotlin service code
- [x] implement windows adapter for MethodChannel and EventChannel
- [x] rewrite new design UI in dart (basic flow)
- [ ] polish new design UI in dart
- [ ] implement linux adapter
- [ ] implement macosx adapter
- [ ] implement web+ios adapter

## How to use

- make sure your device can connect to Internet
  - choose application for downloading (e.g. file\_transfer-1.0.0)
  - choose platform for downloading (e.g. node-v10.10.0)
- application
  - click `play` button to start app via a wizard

### Market Structure

```
/nodebase.json
{
   "version": "...",
   "platform-<os>-<arch>": "...", ...
   "platform-windows-x64": "e.g."
}

/plm-<os>-<arch>.json
{
   "items": [
      "<name>-<version>", ...
      "node-v10.10.0"
   ]
}

/app-<os>-<arch>.json
{
   "items": [
      "<name>-<version>", ...
      "app-1.0.0:node"
   ]
}

/plm/<os>/<arch>/<sha256(utf8(<name>-<version>))>.json
{
   "name": "e.g. node",
   "version": "e.g. v20.10.0",
   "source": "<url>, e.g. https://nodejs.org/dist/v20.11.0/node-v20.11.0-win-x64.zip",
   "executable": ["<relative_executable_path>", "e.g. node-v20.11.0-win-x64\\node.exe"]
}

/app/<os>/<arch>/<sha256(utf8(<name>-<version>))>.json
{
   "name": "e.g. file_transfer",
   "version": "e.g. 1.0",
   "source": "<url>, e.g. https://raw.githubusercontent.com/wiki/dna2github/NodeBase/quick/app/node/file-transfer.zip",
   "type": "web.server",
   "argRequire": [{ "help": "folder path", "default": "." }],
   "envRequire": [],
   "entryPoint": ["index.js"]
}

```

App examples: [https://github.com/nodebase0](https://github.com/nodebase0), includes file-viewer-uploader, nodepad, ...


## Development

This project is a starting point for a Flutter application.

A few resources to get you started if this is your first Flutter project:

- [Lab: Write your first Flutter app](https://docs.flutter.dev/get-started/codelab)
- [Cookbook: Useful Flutter samples](https://docs.flutter.dev/cookbook)

For help getting started with Flutter development, view the
[online documentation](https://docs.flutter.dev/), which offers tutorials,
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

