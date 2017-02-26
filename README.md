# NodeBase
Android NodeJS Platform to Build Sharable Application

# How to use

- (notice that in this repo, there is no NodeJS binary provided, [download (ARM)](https://github.com/dna2github/dna2oslab/releases)) put compiled NodeJS binrary to `app/src/main/res/raw/bin_node_v710`
- (if not use name of `bin_node_v710`) modify `app/src/main/java/seven/drawalive/nodebase/Utils.java` at `R.raw.bin_node_v710`
- build to generate apk
- install the apk on Android phone
- do `npm install` in `modules` folder
- adb push entire `modules` as `/sdcard/.nodebase`

# Modules

#### Screenshots

<img src="https://raw.githubusercontent.com/wiki/dna2github/NodeBase/images/v0/file_download_upload.png" width="200" />
<img src="https://raw.githubusercontent.com/wiki/dna2github/NodeBase/images/v0/werewolf_host.png" width="200" />
<img src="https://raw.githubusercontent.com/wiki/dna2github/NodeBase/images/v0/werewolf_player.png" width="200" />
<img src="https://raw.githubusercontent.com/wiki/dna2github/NodeBase/images/v0/nodepad.png" width="200" />
