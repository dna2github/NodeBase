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

app.get('/test', (req, res) => {
   send_json(res, { ip: get_ip(req), message: 'hello world!' });
});

app.use('/', express.static(static_dir));

app.listen(port, addr, () => {
   console.log(`<app name> is listening at ${addr}:${port}`);
});
