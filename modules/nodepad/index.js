const path = require('path');
const fs = require('fs');
const express = require('express');
const body_parser = require('body-parser');
const app = express();

const static_dir = path.join(__dirname, 'static');

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

app.use(body_parser.urlencoded({ extended: false }));
app.use(body_parser.json());

app.get('/test', (req, res) => {
   res.send('hello world! ' + get_ip(req));
});

app.post('/api/nodebase/nodepad/v1/list', (req, res) => {
   if (!req.body) return res.sendStatus(400);
   if (!req.body.path) return res.sendStatus(400);
   let parent = req.body.path,
       symbols = fs.readdirSync(parent),
       files = [],
       dirs = [];
   symbols.forEach((x) => {
      try {
         if (fs.lstatSync(path.join(parent, x)).isDirectory()) {
            dirs.push(x);
         } else {
            files.push(x);
         }
      } catch (e) {
         // no permission
      }
   });
   send_json(res, { dirs, files });
});

app.post('/api/nodebase/nodepad/v1/open', (req, res) => {
   let file = req.body.path;
   send_json(res, {
      path: file,
      text: fs.readFileSync(file).toString()
   });
});

app.post('/api/nodebase/nodepad/v1/save', (req, res) => {
   let file = req.body.path,
       text = req.body.text;
   fs.writeFileSync(file, text);
   send_json(res, { path: file });
});

app.use('/', express.static(static_dir));

app.listen(9090, '0.0.0.0', () => {
   console.log(`Nodepad is listening at 0.0.0.0:9090`);
});
