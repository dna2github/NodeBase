const http = require('http');
const url = require('url');
const mime = require('mime');
const path = require('path');
const fs = require('fs');

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

function make_directory(dir) {
   dir = path.resolve(dir);
   let parent_dir = path.dirname(dir);
   let state = true;
   if (dir !== parent_dir) {
      if (!fs.existsSync(parent_dir)) {
         state = make_directory(parent_dir);
      } else {
         if (!fs.lstatSync(parent_dir).isDirectory()) {
            state = false;
         }
      }
      if (!state) {
         return null;
      }
   }
   if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir);
      return dir;
   } else if (!fs.lstatSync(dir).isDirectory()) {
      return null;
   } else {
      return dir;
   }
}

function process_app_import(req, res, api, appname, appfiles, rename) {
   function random_tmp_name() {
      return 'tmp-' + Math.random();
   }
   function download_file(api, appname, appfiles, index, tmpdir, cb) {
      if (index >= appfiles.length) {
         cb();
         return;
      }
      if (!appfiles[index]) {
         download_file(api, appname, appfiles, index+1, tmpdir, cb);
         return;
      }
      let filename = appfiles[index];
      let tmpfile = path.join(tmpdir, filename);
      let subtmpdir = path.dirname(tmpfile);
      make_directory(subtmpdir);
      let file = fs.createWriteStream(tmpfile);
      let request = http.get(api + '/download/' + appname + '/' + filename, (obj) => {
         obj.pipe(file);
         download_file(api, appname, appfiles, index+1, tmpdir, cb);
      }).on('error', (e) => {
         errors.push('failed to download: ' + appfiles[index]);
         download_file(api, appname, appfiles, index+1, tmpdir, cb);
      });
   }
   let errors = [];
   let tmpdir = path.join(Storage.work_dir, random_tmp_name());
   let targetdir = path.join(Storage.work_dir, rename);
   if (fs.existsSync(targetdir)) {
      res.end('app exists: ' + rename);
      return;
   }
   fs.mkdirSync(tmpdir);
   download_file(api, appname, appfiles, 0, tmpdir, () => {
      if (errors.length > 0) {
         Storage.rmtree(tmpdir);
         res.end(errors.join('\n'));
         return;
      }
      fs.renameSync(tmpdir, targetdir);
      res.end('');
   });
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

const Storage = {
   work_dir: path.dirname(__dirname),
   list_directories: (dir) => {
      return fs.readdirSync(dir).filter((name) => {
         let subdir = path.join(dir, name);
         let state = fs.lstatSync(subdir);
         return state.isDirectory();
      });
   },
   list_files: (dir) => {
      let queue = [dir], list = [];
      while (queue.length > 0) {
         list_dir(queue.shift(), queue, list);
      }
      return list;

      function list_dir(dir, queue, list) {
         fs.readdirSync(dir).forEach((name) => {
            let filename = path.join(dir, name);
            let state = fs.lstatSync(filename);
            if (state.isDirectory()) {
               queue.push(filename);
            } else {
               list.push(filename);
            }
         });
      }
   },
   rmtree: (dir) => {
      if (dir.length < Storage.work_dir.length) {
         return false;
      }
      if (dir.indexOf(Storage.work_dir) !== 0) {
         return false;
      }
      if (!fs.existsSync(dir)) {
         return false;
      }
      fs.readdirSync(dir).forEach(function(file, index){
         var curPath = path.join(dir, file);
         if (fs.lstatSync(curPath).isDirectory()) {
            // recurse
            Storage.rmtree(curPath);
         } else { // delete file
            fs.unlinkSync(curPath);
         }
      });
      fs.rmdirSync(dir);
      return true;
   }
};

const router = {
   app: {
      list: (req, res, options) => {
         res.setHeader('Access-Control-Allow-Origin', '*');
         let dir = path.dirname(__dirname);
         if (!fs.existsSync(dir)) {
            return router.code(req, res, 404, 'Not Found');
         }
         let names = Storage.list_directories(Storage.work_dir).filter((name) => {
            if (name.startsWith('.')) return false;
            if (name === 'node_modules') return false;
            return true;
         });
         res.end(names.join('\n'));
      },
      files: (req, res, options) => {
         res.setHeader('Access-Control-Allow-Origin', '*');
         let name = options.path[0];
         if (!name) {
            return router.code(req, res, 404, 'Not Found');
         }
         let subdir = path.join(Storage.work_dir, name);
         if (!fs.existsSync(subdir)) {
            return router.code(req, res, 404, 'Not Found');
         }
         let files = Storage.list_files(subdir).map((filename) => {
            return filename.substring(subdir.length+1);
         });
         res.end(files.join('\n'));
      },
      download: (req, res, options) => {
         res.setHeader('Access-Control-Allow-Origin', '*');
         if (options.path.indexOf('..') >= 0) {
            return router.code(req, res, 404, 'Not Found');
         }
         let filename = path.join(...options.path);
         filename = path.join(Storage.work_dir, filename);
         if (!fs.existsSync(filename)) {
            return router.code(req, res, 404, 'Not Found');
         }
         res.setHeader('Content-Disposition', 'attachment; filename=' + path.basename(filename));
         res.setHeader('Content-Type', 'application/octet-stream');
         // res.setHeader('Content-Type', 'text/plain');
         let buf = fs.readFileSync(filename);
         res.end(buf, 'binary');
      },
      delete: (req, res, options) => {
         let name = options.path[0];
         if (!name) {
            return router.code(req, res, 404, 'Not Found');
         }
         let filename = path.join(Storage.work_dir, name);
         // !!!! dangerous action
         Storage.rmtree(filename);
         res.end('');
      },
      import: (req, res, options) => {
         // options.path [ip:port, appname, rename]
         let host = options.path[0];
         let appname = options.path[1];
         let name = options.path[2] || appname;
         let api = 'http://' + host + '/app';
         http.get(api + '/files/' + appname, (obj) => {
            if (~~(obj.statusCode/100) !== 2) {
               obj.resume();
               return router.code(req, res, 404, 'Not Found');
            }
            let raw = '';
            obj.on('data', (chunk) => { raw += chunk; });
            obj.on('end', () => {
               process_app_import(req, res, api, appname, raw.split('\n'), name);
            });
         }).on('error', (e) => {
            return router.code(req, res, 404, 'Not Found');
         });
      }
   },
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

const instance = server.listen(20180, '0.0.0.0', () => {
   console.log(instance.address());
   console.log(`NodeBase Application Manager is listening at 0.0.0.0:20180`);
});
