# Simple MXNetJS Example
==========

- Copy `Emscripten` version of mxnet.js and related models from: https://github.com/dmlc/mxnet.js/
- Modify `test_on_node.js` and addd `Jimp` for convert image data into array

- run `prepare_mxnet.sh` to download mxnet.js and models
- upload image to `images` folder
- push app to android device
- run `node index.js` then visit `http://127.0.0.1:9090/test/cat.jpg` (`cat.jpg` is in `images` folder)
