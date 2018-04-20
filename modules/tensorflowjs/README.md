TensorflowJS MobileNet Demo

if you would like to localize the site (make it offline),
please download model.json and groupN-shard1of1 (N=1,2,3,...,55) to static folder;
and download packaed javascript file of tfjs-examples-mobilenet.js
and download related css file as well
then replace the Internet path to local relative path like `static/tfjs-examples-mobilenet.js` and `static/material.cyan-teal.min.css`
then push it to your Android and run it offline! :)

```
https://github.com/tensorflow/tfjs-examples/tree/master/mobilenet

Keras original model:
https://github.com/fchollet/deep-learning-models/releases/download/v0.6/mobilenet_2_5_224_tf.h5

TensorflowJS model
https://storage.googleapis.com/tfjs-models/tfjs/mobilenet_v1_0.25_224/model.json
group1-group55
https://storage.googleapis.com/tfjs-models/tfjs/mobilenet_v1_0.25_224/group1-shard1of1

Packed JavaScript
https://storage.googleapis.com/tfjs-examples/mobilenet/dist/tfjs-examples-mobilenet.js
```
