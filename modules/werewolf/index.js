const path = require('path');
const express = require('express');
const app = express();

const werewolf = require('./werewolf/werewolf');

const static_dir = path.join(__dirname, 'static');

let start_one_role = 0,
    werewolf_role = [];

function ip_decode(ip) {
   return ip?ip.split('-').join('.'):ip;
}

function fake_act(delay) {
   setTimeout(() => {
      start_one_role = 0;
   }, delay);
}

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
   res.send('hello world! ' + get_ip(req));
});

app.post('/api/info', (req, res) => {
   let ip = get_ip(req),
       p = werewolf.player_get_obj(ip),
       s = werewolf.state_get(),
       m = '';
   if (!p) {
      if (s.cur === ' ') {
         p = werewolf.player_register(ip, '');
      } else {
         p = {name: '本局已经开始'};
      }
   } else {
      m = werewolf.player_get_info(ip);
   }
   send_json(res, {name: p?p.name:'', role: p?p.role:null, info: m});
});

app.post('/api/player/register', (req, res) => {
   let ip = get_ip(req),
       p = werewolf.player_get_obj(ip),
       s = werewolf.state_get();
   if (p) {
      if (s.cur !== ' ') {
         // should not change role after game starting
         req.query.role = p.role;
      }
      werewolf.player_register(ip, req.query.name, req.query.role);
      send_json(res, {});
   } else {
      res.sendStatus(400);
   }
});

app.post('/api/player/unregister', (req, res) => {
   let ip = ip_decode(req.query.ip) || get_ip(req);
   werewolf.player_unregister(ip);
   send_json(res, {});
});

app.post('/api/player/alive', (req, res) => {
   let ip = ip_decode(req.query.ip) || get_ip(req),
       p = werewolf.player_get_obj(ip);
   if (p) {
      if (parseInt(req.query.alive, 10) === 1) p.alive = true;
                                          else p.alive = false;
   }
   send_json(res, {});
});

app.post('/api/werewolf/config', (req, res) => {
   let config = req.query;
   for (let role in config) {
      config[role] = parseInt(config[role], 10);
   }
   werewolf.config_set(Object.assign({}, config));
   send_json(res, {});
});

app.post('/api/werewolf/state', (req, res) => {
   let state = req.query.state || null,
       s = null;
   if (state) {
      werewolf.state_set(state);
      if (!werewolf.player_find('role', state)) {
         fake_act(~~(Math.random()*7000+3000)); // 3~10s
      }
      start_one_role = 1;
      s = Object.assign({}, werewolf.state_get());
      if (state === ' ') {
         werewolf_role = [];
      }
   } else {
      let ip = get_ip(req),
          p = werewolf.player_get_obj(ip),
          info = werewolf.player_get_info(ip),
          state = werewolf.state_get();
      if (!p) {
         s = {info: '____', cur: '-'};
      } else if (p.role !== state.cur && state.cur !== ' ') {
         s = {info: '你想干嘛 :)', cur: '-'};
      } else if (!p.alive) {
         s = {info: '僵尸再见 :)', cur: '-'};
      } else {
         s = Object.assign({info}, state);
         if (['x', 'i', 'g', 's'].indexOf(s.cur) >= 0) {
            if (werewolf_role.indexOf(ip) < 0) werewolf_role.push(ip);
         }
      }
   }
   send_json(res, s);
});

app.post('/api/werewolf/acting', (req, res) => {
   let s = Object.assign({start_one_role}, werewolf.state_get());
   // should not exist here when has T
   s.werewolf_count = werewolf_role.length;
   send_json(res, s);
});

app.post('/api/werewolf/act', (req, res) => {
   let id = req.query.id;
       ip = get_ip(req),
       pact = werewolf.player_get_obj(ip),
       state = werewolf.state_get();
   delete req.query.id;
   if (!id) {
      res.sendStatus(400);
   } else if (id !== state.id) {
      res.sendStatus(400);
   } else if (state.cur !== pact.role) {
      res.sendStatus(403);
   } else {
      let s = Object.assign({}, state), p;
      Object.keys(req.query).forEach((x) => {
         req.query[x] = ip_decode(req.query[x]);
         werewolf.actions_set(x, req.query[x]);
         switch(x) {
         case 'see':
            if (req.query[x]) {
               p = werewolf.player_get_obj(req.query[x]);
               // first night, wild child (U) should not be werewolf
               s.info = p.name + ' 是' + (['x', 'g', 'i', 's'].indexOf(p.role)>=0?'狼人':'好人');
            }
            break;
         case 'lover_1':
            if (req.query.lover_1 && req.query.lover_2) {
               werewolf.actions_set('lovers', [req.query.lover_1, req.query.lover_2].join(','));
            } else {
               werewolf.actions_set('lovers', null);
            }
            break;
         }
      });
      werewolf.state_set('-');
      start_one_role = 0;
      send_json(res, s);
   }
});

app.post('/api/werewolf/info', (req, res) => {
   let ps = werewolf.player_all(),
       order = werewolf.player_get_order(),
       ips = Object.keys(ps);
   if (order.length === ips.length) {
      ips = order;
   }
   ps = ips.map((x) => ps[x]);
   send_json(res, {players: ps, config: werewolf.config_get()});
});

app.post('/api/werewolf/reorder', (req, res) => {
   let seq = req.query.seq;
   if (!seq) {
      res.sendStatus(400);
      return;
   }
   seq = seq.split(',').map(ip_decode);
   werewolf.player_reorder(seq);
   send_json(res, {});
});

app.post('/api/werewolf/bigvote', (req, res) => {
   let actions = werewolf.actions_get(),
       bigvote = actions.bigvote?actions.bigvote[0]:null;
   send_json(res, {bigvote});
});

app.post('/api/werewolf/night', (req, res) => {
   let actions = werewolf.actions_get(),
       m = werewolf.night_result();
   m += werewolf.info_hunter();
   m += werewolf.info_bear();
   werewolf.state_set('-');
   send_json(res, {info: m});
});

app.use('/', express.static(static_dir));

app.listen(9090, '0.0.0.0', () => {
   console.log(`Werewolf is listening at 0.0.0.0:9090`);
});
