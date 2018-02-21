# NodeBase
<img src="https://raw.githubusercontent.com/wiki/dna2github/NodeBase/images/log.png" />

Android NodeJS Platform to Build Sharable Application (Android as a Server)

Share application with your friends in the same Wi-Fi!

# How to use

- build to generate apk
- install the apk on Android phone
- click "Reset" in right-top menu will donwload NodeJS binary and copy to app scope target
- (notice that in this repo, there is no NodeJS binary provided, [download latest](https://github.com/dna2github/dna2oslab/releases)) put compiled NodeJS binary to `/sdcard/.nodebase/.bin/node`
- click "Node Upgrade" to update NodeJS binary for NodeBase
- do `npm install` in `modules` folder
   - to make node-gyp work, download GCC4droid from for example Google Play Store and then unzip the apk to get android `gcc`
- adb push entire `modules` as `/sdcard/.nodebase`

# Modules

#### Screenshots

<img src="https://raw.githubusercontent.com/wiki/dna2github/NodeBase/images/v0/file_download_upload.png" width="200" />
<img src="https://raw.githubusercontent.com/wiki/dna2github/NodeBase/images/v0/nodepad.png" width="200" />
