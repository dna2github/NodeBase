const http = require('http');
const url = require('url');
const path = require('path');
const fs = require('fs');
const wrap = require('./wrap');

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

function mime_lookup(filename) {
   let extname = path.extname(filename);
   switch(extname) {
   case '.json': return 'application/json';
   case '.html': return 'text/html';
   case '.js': return 'text/javascript';
   case '.css': return 'text/css';
   default: return 'application/octet-stream'
   }
}


const router = {
   test: (req, res, options) => {
      let image_name = options.path[0];
      wrap.predict(path.join(__dirname, 'images', image_name)).then(function (result) {
         let html = '<html><body>';
         html += '<div><pre>' + result.join('\n') + '</pre></div>';
         html += '<div><img style="width:224px;height:224px;" src="/images/' + image_name + '" /></div>';
         html += '</body></html>';
         res.setHeader('Context-Type', 'text/html');
         res.end(html);
      });
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
      if (filename[0] === 'images') {
         filename = path.join(__dirname, ...filename);
      } else {
         filename = path.join(__dirname, 'static', ...filename);
      }
      if (!fs.existsSync(filename)) {
         return router.code(req, res, 404, 'Not Found');
      }
      res.setHeader('Content-Type', mime_lookup(filename));
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

const instance = server.listen(9090, '0.0.0.0', () => {
   console.log(instance.address());
   console.log(`NodeBase MXNetJS Example is listening at 0.0.0.0:9090`);
});
