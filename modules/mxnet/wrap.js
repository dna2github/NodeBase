const path = require('path');
const jimp = require('jimp');
const mx = require("./mxnet_predict.js");

function runModel(modelJson, image) {
    var result = [];
    var model = require(modelJson);
    pred = new mx.Predictor(model, {'data': [1, 3, 224, 224]});
    pred.setinput('data', image);
    var nleft = 1;

    var start = new Date().getTime();
    var end   = new Date().getTime();
    var time  = (end - start) / 1000;

    for (var step = 0; nleft != 0; ++step) {
      nleft = pred.partialforward(step);
      end = new Date().getTime();
      time = (end - start) / 1000;
    }
    out = pred.output(0);

    out = pred.output(0);
    var index = new Array();
    for (var i=0;i<out.data.length;i++) {
        index[i] = i;
    }
    index.sort(function(a,b) {return out.data[b]-out.data[a];});

    max_output = 10;
    for (var i = 0; i < max_output; i++) {
        result.push(`[${i+1}]: ${model.synset[index[i]]}, PROB=${out.data[index[i]]*100}%`);
    }
    pred.destroy();
    return result;
}


// const fs = require('fs');
// const jpeg = require('jpeg-js');
// var image = jpeg.decode(fs.readFileSync(process.argv[2]));
function predict(imagePath, modelPath) {
   var model = modelPath || path.join(__dirname, 'model', 'squeezenet-model.json');
   return new Promise(function (resolve, reject) {
      jimp.read(imagePath, function (err, buf) {
         buf.resize(224, 224, function (err, buf) {
            var image = {data: buf.bitmap.data, width: 224, height: 224};
            var data = [], r = [], g = [], b = [];
            var i = 0, n = 224*224*4;
            for (i = 0; i < n; i+=4) {
               r.push(image.data[i]);
               g.push(image.data[i+1]);
               b.push(image.data[i+2]);
            }
            data = r.concat(g).concat(b);
            var decoded = new Float32Array(data);
            var image = mx.ndarray(decoded, [1, 3, 224, 224]);
            resolve(runModel(model, image));
         });
      });
   });
}

module.exports = {
   predict: predict
};
