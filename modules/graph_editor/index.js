const path = require('path');
const fs = require('fs');
const express = require('express');
const body_parser = require('body-parser');
const app = express();

const static_dir = path.join(__dirname, 'static');

const graph = require('./lib/graph');
let g = {};

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

app.post('/api/nodebase/graph_editor/v1/list', (req, res) => {
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

app.post('/api/nodebase/graph_editor/v1/open', (req, res) => {
   let file = req.body.path
       ip = get_ip(req),
       localg = new graph.Graph();
   localg.filename = file;
   try {
      localg.load(file);
   } catch (e) {
   }
   g[ip] = localg;
   send_json(res, { path: file });
});

app.post('/api/nodebase/graph_editor/v1/save', (req, res) => {
   let ip = get_ip(req),
       localg = g[ip],
       file = localg.filename;
   delete localg.filename;
   localg.save(file);
   localg.filename = file;
   send_json(res, { path: file });
});

app.post('/api/nodebase/graph_editor/v1/close', (req, res) => {
   let ip = get_ip(req),
       localg = g[ip],
       file = localg.filename;
   delete g[ip];
   send_json(res, { path: file });
});

app.post('/api/nodebase/graph_editor/v1/search', (req, res) => {
   let ip = get_ip(req),
       query = req.body.q,
       localg = g[ip],
       file = localg.filename;
   nodes = localg.nodeFilter('name', query, (node_name, query) => {
      if (!query) return true;
      query = query.split(' ');
      return query.map(
         (x) => node_name.indexOf(x) >= 0
      ).reduce(
         (x, y) => x||y
      );
   });
   nodes = nodes.map((node) => {
      node = Object.assign({}, node);
      delete node.links;
      return node;
   })
   send_json(res, { nodes });
});

app.post('/api/nodebase/graph_editor/v1/add', (req, res) => {
   let ip = get_ip(req),
       localg = g[ip],
       node_names = req.body.names;
   send_json(res, { nodes: node_names.map((x) => localg.nodeAdd({name: x})) });
});

app.get('/api/nodebase/graph_editor/v1/node/:id', (req, res) => {
   let ip = get_ip(req),
       localg = g[ip],
       node = localg.nodes[req.params.id];
   node = Object.assign({}, node);
   node.links = node.links.map((eid) => {
      let another_node = null,
          link = localg.links[eid],
          from_another_node = true;
      if (link.from === node.id) {
         another_node = localg.nodes[link.to];
         from_another_node = false;
      } else {
         another_node = localg.nodes[link.from];
      }
      if (!another_node) return null;
      another_node = Object.assign({
         _from: from_another_node,
         _to: !from_another_node
      }, another_node);
      delete another_node.links;
      return another_node;
   });
   send_json(res, { node });
});

app.delete('/api/nodebase/graph_editor/v1/node/:id', (req, res) => {
   let ip = get_ip(req),
       localg = g[ip];
   localg.nodeDel(req.params.id);
   send_json(res, { node: 1 });
});

app.post('/api/nodebase/graph_editor/v1/link/:fromid/:toid', (req, res) => {
   let ip = get_ip(req),
       localg = g[ip],
       link = localg.linkAdd(
          parseInt(req.params.fromid, 10),
          parseInt(req.params.toid, 10)
       );
   send_json(res, { link });
});

app.delete('/api/nodebase/graph_editor/v1/link/:fromid/:toid', (req, res) => {
   let ip = get_ip(req),
       localg = g[ip],
       node = localg.nodes[req.params.fromid],
       count = 0;
   node.links.forEach((eid) => {
      let link = localg.links[eid],
          fromid = parseInt(req.params.fromid, 10),
          toid = parseInt(req.params.toid, 10);
      if (link.from === fromid && link.to === toid) {
         localg.linkDel(link.id);
         count ++;
      }
   });
   send_json(res, { link: count });
});

app.use('/', express.static(static_dir));

app.listen(9090, '127.0.0.1', () => {
   console.log(`Graph Editor is listening at 127.0.0.1:9090`);
});
