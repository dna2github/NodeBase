#!/bin/bash

git clone https://github.com/dmlc/mxnet.js/
cd mxnet.js
cp -r libmxnet_predict.js libmxnet_predict.js.mem mxnet_predict.js model ../

# patch file to make sure libmxnet_predict.js.mem can be found
cd ..
# macosx sed -i '' 's...'
sed -i 's|"libmxnet_predict.js.mem"|__dirname+"/libmxnet_predict.js.mem"|g' libmxnet_predict.js
