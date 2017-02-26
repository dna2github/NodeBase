# NodeBase
Android NodeJS Platform to Build Sharable Application

# How to use

- (notice that in this repo, there is no NodeJS binary provided, [download (ARM)](https://github.com/dna2github/dna2oslab/releases)) put compiled NodeJS binrary to `app/src/main/res/raw/bin_node_v710`
- (if not use name of `bin_node_v710`) modify `app/src/main/java/seven/drawalive/nodebase/Utils.java` at `R.raw.bin_node_v710`
- build to generate apk
- install the apk on Android phone
- do `npm install` in `modules` folder
- adb push entire `modules` to `/sdcard/.nodebase`
