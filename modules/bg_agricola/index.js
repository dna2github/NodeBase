const buffer = require('buffer');
const path = require('path');
const express = require('express');
const app = express();

const static_dir = path.join(__dirname, 'static');

const addr = '0.0.0.0';
const port = 9090;

function send_json(res, obj) {
   res.setHeader('Content-Type', 'application/json');
   res.send(JSON.stringify(obj));
}

function get_ip (req) {
   let ip = null;
   if (req.headers['x-forwarded-for']) {
      ip = req.headers['x-forwarded-for'].split(",")[0];
   } else if (req.connection && req.connection.remoteAddress) {
      ip = req.connection.remoteAddress;
   } else {
      ip = req.ip;
   }
   return ip;
}

function body (req, fn) {
   let buf = new buffer.Buffer([]), obj;
   req.on('data', (data) => {
      buf = buffer.Buffer.concat([buf, buffer.Buffer.from(data)]);
   }).on('end', () => {
      try {
         obj = JSON.parse(buf);
      } catch (e) {
         obj = null;
      }
      fn && fn(obj);
   });
}

app.get('/test', (req, res) => {
   send_json(res, { ip: get_ip(req), message: 'hello world!' });
});

let objs = [], timestamp = 0;

app.post('/api/set', (req, res) => {
   body(req, (reqbody) => {
      objs = reqbody.objs;
      timestamp = new Date().getTime()
      send_json(res, { ip: get_ip(req), timestamp });
   });
});

app.post('/api/get', (req, res) => {
   send_json(res, { ip: get_ip(req), objs, timestamp, now: new Date().getTime()+1 });
});

app.use('/', express.static(static_dir));

app.listen(port, addr, () => {
   console.log(`Agricola is listening at ${addr}:${port}`);
});
