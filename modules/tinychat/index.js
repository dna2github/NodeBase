const http = require('http');
const url = require('url');
const mime = require('mime');
const path = require('path');
const fs = require('fs');
const ws = require('ws');


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

function route(req, res) {
   let r = url.parse(req.url);
   let f = router;
   let path = r.pathname.split('/');
   let query = {};
   r.query && r.query.split('&').forEach((one) => {
      let key, val;
      let i = one.indexOf('=');
      if (i < 0) {
         key = one;
         val = '';
      } else {
         key = one.substring(0, i);
         val = one.substring(i+1);
      }
      if (key in query) {
         if(Array.isArray(query[key])) {
            query[key].push(val);
         } else {
            query[key] = [query[key], val];
         }
      } else {
         query[key] = val;
      }
   });
   path.shift();
   while (path.length > 0) {
      let key = path.shift();
      f = f[key];
      if (!f) break;
      if (typeof(f) === 'function') {
         return f(req, res, {
            path: path,
            query: query
         });
      }
   }
   router.static(req, res, r.pathname);
   // router.code(req, res, 404, 'Not Found');
}

const router = {
   test: (req, res, options) => {
      res.end('hello');
   },
   static: (req, res, filename) => {
      if (!filename || filename === '/') {
         filename = 'index.html';
      }
      filename = filename.split('/');
      if (!filename[0]) filename.shift();
      if (filename.length === 0 || filename.indexOf('..') >= 0) {
         return router.code(req, res, 404, 'Not Found');
      }
      filename = path.join(__dirname, 'static', ...filename);
      if (!fs.existsSync(filename)) {
         return router.code(req, res, 404, 'Not Found');
      }
      res.setHeader('Content-Type', mime.lookup(filename));
      let buf = fs.readFileSync(filename);
      res.end(buf, 'binary');
   },
   code: (req, res, code, text) => {
      res.writeHead(code || 404, text || '');
      res.end();
   }
};

const server = http.createServer((req, res) => {
   route(req, res);
});
const wssrv = new ws.Server({
   server: server,
   path: '/ws'
});

function process_message(ws, env, obj) {
   let tmp;
   switch(obj.cmd) {
      case 'name':
      if (obj.value in clients) {
         ws.send(JSON.stringify({
            error: 'name'
         }));
         return;
      }
      clients[obj.value] = {
         name: obj.value,
         ws: ws
      };
      env.name = obj.value;
      ws.send(JSON.stringify({
         ack: 'name'
      }));
      break;
      case 'talk':
      tmp = {
         name: env.name,
         message: obj.value
      };
      Object.keys(clients).forEach((name) => {
         if (name === env.name) return;
         clients[name].ws.send(JSON.stringify(tmp));
      });
      ws.send(JSON.stringify({
         ack: 'talk'
      }));
      break;
   }
}

const clients = {};
wssrv.on('connection', (client) => {
   let env = {
      name: null,
      timer: null,
   };
   client.on('open', () => {
      console.log('[debug]', 'connected ...');
      env.timer = setInterval(() => {
         client.ping();
      }, 20*1000);
   });
   client.on('message', (message) => {
      console.log('[debug]', message);
      try {
         process_message(client, env, JSON.parse(message));
      } catch(e) {}
   });
   client.on('close', () => {
      if (!env.name) {
         console.log('[debug]', env.name + ' closed ...');
         delete clients[env.name];
         clearInterval(env.timer);
      }
   });
   client.on('error', () => {
      if (!env.name) {
         console.log('[debug]', env.name + ' error ...');
         delete clients[env.name];
         clearInterval(env.timer);
      }
   })
});

const instance = server.listen(9091, '0.0.0.0', () => {
   console.log(instance.address());
   console.log(`NodeBase TinyBot is listening at 0.0.0.0:9091`);
});
