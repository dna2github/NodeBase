const http = require('http');
const url = require('url');
const mime = require('mime');
const path = require('path');
const fs = require('fs');
let html_index = `<!doctype html>
<html>
  <head>
    <meta charset='utf-8'>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <title>NodeBase Appliction Manager</title>
    <style text="text/css">
    body {
       overflow-x: hidden;
    }
    .disabled {
       pointer-events: none;
    }
    .item {
       display: block;
       width: 100%;
       margin-top: 2px;
       padding: 10px 0px 10px 10px;
       text-decoration: none;
       color: black;
    }
    .item-r {
       display: block;
       width: 100%;
       margin-top: 2px;
       margin-left: -10px;
       padding: 10px 10px 10px 0;
       text-decoration: none;
       text-align: right;
       color: black;
    }
    .btn {
       display: inline-block;
       padding: 5px;
       background-color: white;
       border-radius: 3px;
       border: 1px solid black;
    }
    .badge {
       border: 1px solid black;
       padding: 0 5px 0 5px;
       margin-left: 10px;
    }
    .input {
       padding: 5px;
       border-top: 0;
       border-left: 0;
       border-right: 0;
       border-bottom: 1px solid black;
       width: 100%;
    }
    a.item-r:hover {
       opacity: 0.5;
       cursor: pointer;
    }
    a.item:hover {
       opacity: 0.5;
       cursor: pointer;
    }
    .btn:hover {
       border: 1px solid black;
       color: white;
       background-color: black;
    }
    .hide {
       display: none;
    }
    .grey   { background-color: #e2e2e2; }
    .red    { background-color: #f5cdcd; }
    .green  { background-color: #cff5cd; }
    .blue   { background-color: #cdebf5; }
    .yellow { background-color: #fbf59f; }
    .orange { background-color: #ffe6cc; }
    .pink   { background-color: #f5cde8; }
    .purple { background-color: #dfcdf5; }
    </style>
  </head>
  <body>
    <div class="item">NodeBase Application Manager</div>

    <div class="item yellow">Ensure it is running in a safe Wi-Fi environment; otherwise data can be lost accidently by others or malwares.</div>
    <div id="panel_loading">
      <div id="txt_message" class="item yellow">Loading ...</div>
    </div>

    <div id="panel_entry" class="hide">
      <!-- <div class="item grey">Server <span id="txt_host" class="badge">IP:PORT</span></div> -->
      <div class="item green">Local Applications</div>
      <!--
      <div class="item">
         <input id="txt_workdir" class="input" placeholder="local dirctory, e.g. /sdcard/.nodebase"/>
      </div>
      -->
      <div><a id="btn_enter_local" class="item-r blue" href="#">Enter</a></div>
      <br />
      <div class="item green">Shared Application</div>
      <div class="item">
         <input id="txt_server" class="input" placeholder="another server, e.g. 192.168.1.101:20180"/>
      </div>
      <div><a id="btn_enter_server" class="item-r blue" href="#">Enter</a></div>
      <br />
      <!--
      <div class="item green">NPM Installation</div>
      <div><a id="btn_install_npm" class="item-r blue" href="#">Install</a></div>
      -->
    </div>

    <div id="panel_list" class="hide">
      <div><a id="btn_back_home" class="item-r grey" href="#">Back</a></div>
      <div class="item green">Application List</div>
      <div id="list_apps"></div>
    </div>

    <div id="panel_app" class="hide">
      <div><a id="btn_back_list" class="item-r grey" href="#">Back</a></div>
      <div class="item green">Application</div>
      <div class="item grey">Name <span id="txt_appname" class="badge">name</span></div>
      <!-- <div class="item grey">Description <p id="txt_appdesc">description detail balabala...</p></div> -->
      <div><a id="btn_import" class="item-r blue" href="#">Import</a></div>
      <!-- <div><a id="btn_npm_install" class="item-r blue" href="#">NPM Install</a></div> -->
      <div><a id="btn_delete" class="item-r red" href="#">Delete</a></div>
    </div>

    <script>
    function id(name) {
       return document.getElementById(name);
    }
    function elem_clear(elem) {
       while (elem.hasChildNodes()) elem.removeChild(elem.lastChild);
    }
    function elem_settext(elem, text) {
       elem_clear(elem);
       elem.appendChild(document.createTextNode(text));
    }
    function on(name, event, fn) {
       id(name).addEventListener(event, fn);
    }

    var app = {};
    app.data = {
       /* apps = [] */
       apps: [],
       server: null
    };
    app.ui = {
       switch_panel: function (panel) {
          var panel_list = ['panel_loading', 'panel_entry', 'panel_list', 'panel_app'];
          panel_list.forEach(function (x) {
             id(x).classList.add('hide');
          });
          id(panel).classList.remove('hide');
       },
       build_app_list: function (apps) {
          var m_list_apps = id('list_apps');
          elem_clear(m_list_apps);
          apps.forEach(function (appname) {
             var m_one = document.createElement('a');
             m_one.classList.add('item');
             m_one.classList.add('blue');
             elem_settext(m_one, appname);
             m_list_apps.appendChild(m_one);
          });
       }
    };
    app.api = function (options, done_fn, fail_fn) {
       var xhr = new XMLHttpRequest(), payload = null;
       xhr.open(options.method || 'POST', options.url + (options.data?uriencode(options.data):''), true);
       xhr.addEventListener('readystatechange', function (evt) {
          if (evt.target.readyState === 4 /*XMLHttpRequest.DONE*/) {
             if (~~(evt.target.status/100) === 2) {
                done_fn && done_fn(evt.target.response);
             } else {
                fail_fn && fail_fn(evt.target.status);
             }
          }
       });
       if (options.json) {
          xhr.setRequestHeader("Content-Type", "application/json;charset=UTF-8");
          payload = JSON.stringify(options.json);
       }
       xhr.send(payload);
    };

    on('btn_back_home', 'click', function (evt) {
       app.ui.switch_panel('panel_entry');
    });
    on('btn_back_list', 'click', function (evt) {
       app.ui.switch_panel('panel_list');
    });
    on('btn_enter_local', 'click', function (evt) {
       app.data.server = null;
       app.ui.switch_panel('panel_loading');
       app.api(
          {method: 'GET', url: '/app/list'},
          function (text) {
             app.ui.build_app_list(text.split('\\n'));
             app.ui.switch_panel('panel_list');
          },
          function () {
            app.data.server = null;
            app.ui.switch_panel('panel_entry');
          }
       );
    });
    on('btn_enter_server', 'click', function (evt) {
       app.data.server = id('txt_server').value;
       app.ui.switch_panel('panel_loading');
       app.api(
          {method: 'GET', url: 'http://' + app.data.server + '/app/list'},
          function (text) {
             app.ui.build_app_list(text.split('\\n'));
             app.ui.switch_panel('panel_list');
          },
          function () {
            app.data.server = null;
            app.ui.switch_panel('panel_entry');
          }
       );
    });
    on('list_apps', 'click', function (evt) {
       if (evt.target.tagName.toLowerCase() !== 'a') {
          return;
       }
       var name = evt.target.textContent;
       elem_settext(id('txt_appname'), name);
       if (app.data.server) {
          id('btn_import').classList.remove('hide');
          id('btn_delete').classList.add('hide');
       } else {
          id('btn_import').classList.add('hide');
          id('btn_delete').classList.remove('hide');
       }
       app.ui.switch_panel('panel_app');
    });
    on('btn_delete', 'click', function (evt) {
       app.ui.switch_panel('panel_loading');
       var name = id('txt_appname').textContent;
       app.api(
          {method: 'GET', url: '/app/delete/' + name},
          function (text) {
             app.ui.switch_panel('panel_entry');
          },
          function () {
            app.data.server = null;
            app.ui.switch_panel('panel_entry');
          }
       );
    });
    on('btn_import', 'click', function (evt) {
      app.ui.switch_panel('panel_loading');
      var name = id('txt_appname').textContent;
       app.api(
          {method: 'GET', url: '/app/import/' + app.data.server + '/' + name},
          function (text) {
             app.ui.switch_panel('panel_list');
          },
          function () {
            app.ui.switch_panel('panel_app');
          }
       );
    });
    app.ui.switch_panel('panel_entry');
    </script>
  </body>
</html>`;

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
      if (!fs.existsSync(subtmpdir)) {
         fs.mkdirSync(subtmpdir);
      }
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
         //res.setHeader('Content-Type', 'application/octet-stream');
         res.setHeader('Content-Type', 'text/plain');
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
         res.end(html_index, 'utf-8');
         return;
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
   console.log(`NodeBase Application Manager is listening at 0.0.0.0:20180`);
});
