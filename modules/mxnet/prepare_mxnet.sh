#!/bin/bash

git clone https://github.com/dmlc/mxnet.js/
cd mxnet.js
cp -r libmxnet_predict.js libmxnet_predict.js.mem mxnet_predict.js model ../
