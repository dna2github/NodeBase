var module = {
   exports: {}
};

(function () {
// https://github.com/karlwestin/node-tonegenerator
/*
 * ToneGenerator for node.js
 * generates raw PCM data for a tone,
 * specify frequency, length, volume and sampling rate
 */

var shapes = {
  sine: function (i, cycle, volume) {
    // i / cycle => value between 0 and 1
    // 0 = beginning of circly
    // 0.25 Math.sin = 1
    // 0.5 Math.sin = 0
    // 0.75 Math.sin = -1
    // 1 Math.sin = 1
    return Math.min(volume * Math.sin((i/cycle) * Math.PI * 2), volume - 1);
  },
  triangle: function (i, cycle, volume) {
    var halfCycle = cycle / 2
    var level

    if (i < halfCycle) {
      level = (volume * 2) * (i / halfCycle) - volume;
    } else {
      i = i - halfCycle
      level = -(volume * 2) * (i / halfCycle) + volume;
    }

    return Math.min(level, volume - 1);
  },
  saw: function (i, cycle, volume) {
    return Math.min((volume * 2) * (i / cycle) - volume, volume - 1);
  },
  square: function (i, cycle, volume) {
    if(i > cycle / 2) {
      return volume - 1;
    }

    return -volume;
  }
}

function generateCycle(cycle, volume, shape) {
  var data = [];
  var tmp;
  var generator = typeof shape == 'function' ? shape : shapes[shape];
  if (!generator) {
    throw new Error('Invalid wave form: "' + shape + '" choose between: ' + Object.keys(shapes));
  }

  for(var i = 0; i < cycle; i++) {
    tmp = generator(i, cycle, volume);
    data[i] = Math.round(tmp);
  }
  return data;
}

function generateWaveForm(opts) {
  opts = opts || {}
  var freq = opts.freq || 440;
  var rate = opts.rate || 22050
  var lengthInSecs = opts.lengthInSecs || 2.0;
  var volume = opts.volume || 30;
  var shape = opts.shape || 'sine';

  var cycle = Math.floor(rate/freq);
  var samplesLeft = lengthInSecs * rate;
  var cycles = samplesLeft/cycle;
  var ret = [];

  for(var i = 0; i < cycles; i++) {
    ret = ret.concat(generateCycle(cycle, volume, shape));
  }
  return ret;
};

module.exports.tone = function() {
  // to support both old interface and the new one:
  var opts = arguments[0]
  if (arguments.length > 1 && typeof opts === "number") {
    opts = {}
    opts.freq = arguments[0]
    opts.lengthInSecs = arguments[1]
    opts.volume = arguments[2]
    opts.rate = arguments[3]
  }

  return generateWaveForm(opts)
}

module.exports.MAX_16 = 32768;
module.exports.MAX_8 = 128;

})();

(function() {

function pack_32b_le(num) {
   var a = 0, b = 0, c = 0, d = 0;
   a = num % 256;
   num >>= 8;
   b = num % 256;
   num >>= 8;
   c = num % 256;
   num >>= 8;
   d = num % 256;
   return (
      String.fromCharCode(a) +
      String.fromCharCode(b) +
      String.fromCharCode(c) +
      String.fromCharCode(d)
   );
}

function pack_16b_le(num) {
   var a = 0, b = 0;
   a = num % 256;
   num >>= 8;
   b = num % 256;
   return (
      String.fromCharCode(a) +
      String.fromCharCode(b)
   );
}
// https://github.com/karlwestin/node-waveheadera
// modified to browserify :-- Seven Lju

/*
 * WaveHeader
 *
 * writes a pcm wave header to a buffer + returns it
 *
 * taken form
 * from github.com/tooTallNate/node-wav
 * lib/writer.js
 *
 * the only reason for this module to exist is that i couldn't
 * understand how to use the one above, so I made my own.
 * You propably wanna use that one
 */
module.exports.wavheader = function generateHeader(length, options) {
  options = options || {};
  var RIFF = 'RIFF';
  var WAVE = 'WAVE';
  var fmt  = 'fmt ';
  var data = 'data';

  var MAX_WAV = 4294967295 - 100;
  var format = 1; // raw PCM
  var channels = options.channels || 1;
  var sampleRate = options.sampleRate || 44100;
  var bitDepth = options.bitDepth || 8;

  var headerLength = 44;
  var dataLength = length || MAX_WAV;
  var fileSize = dataLength + headerLength;
  var header = '';

  // write the "RIFF" identifier
  header += RIFF;
  // write the file size minus the identifier and this 32-bit int
  header += pack_32b_le(fileSize - 8);
  // write the "WAVE" identifier
  header += WAVE;
  // write the "fmt " sub-chunk identifier
  header += fmt;

  // write the size of the "fmt " chunk
  // XXX: value of 16 is hard-coded for raw PCM format. other formats have
  // different size.
  header += pack_32b_le(16);
  // write the audio format code
  header += pack_16b_le(format);
  // write the number of channels
  header += pack_16b_le(channels);
  // write the sample rate
  header += pack_32b_le(sampleRate);
  // write the byte rate
  var byteRate = sampleRate * channels * bitDepth / 8;
  header += pack_32b_le(byteRate);
  // write the block align
  var blockAlign = channels * bitDepth / 8;
  header += pack_16b_le(blockAlign);
  // write the bits per sample
  header += pack_16b_le(bitDepth);
  // write the "data" sub-chunk ID
  header += data;
  // write the remaining length of the rest of the data
  header += pack_32b_le(dataLength);

  // flush the header and after that pass-through "dataLength" bytes
  return header;
};

})();
