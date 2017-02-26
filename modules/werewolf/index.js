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
   send_json(res, {name: p?p.name: '', info: m});
});

app.post('/api/player/register', (req, res) => {
   let ip = get_ip(req),
       p = werewolf.player_get_obj(ip);
   if (p) {
      werewolf.player_register(ip, req.query.name);
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

app.post('/api/werewolf/state', (req, res) => {
   let state = req.query.state || null,
       s = null;
   if (state) {
      werewolf.state_set(state);
      start_one_role = 1;
      s = Object.assign({}, werewolf.state_get());
      if (state === ' ') {
         werewolf_role = [];
      }
   } else {
      let ip = get_ip(req),
          p = werewolf.player_get_obj(ip),
          info = werewolf.player_get_info(ip);
      s = Object.assign({info}, werewolf.state_get());
      if (['x', 'i', 'g'].indexOf(s.cur) >= 0) {
         if (werewolf_role.indexOf(ip) < 0) werewolf_role.push(ip);
         p.role = 'x';
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
   delete req.query.id;
   if (!id) {
      res.sendStatus(400);
   } else if (id !== werewolf.state_get().id) {
      res.sendStatus(400);
   } else {
      let s = Object.assign({}, werewolf.state_get()), p;
      Object.keys(req.query).forEach((x) => {
         req.query[x] = ip_decode(req.query[x]);
         werewolf.actions_set(x, req.query[x]);
         switch(x) {
         case 'see':
            if (req.query[x]) {
               p = werewolf.player_get_obj(req.query[x]);
               s.info = p.name + ' 是' + (p.role === 'x'?'狼人':'好人');
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
      start_one_role = 0;
      send_json(res, s);
   }
});

app.post('/api/werewolf/info', (req, res) => {
   let ps = werewolf.player_all();
   ps = Object.keys(ps).map((x) => ps[x]);
   send_json(res, {players: ps});
});

app.post('/api/werewolf/bigvote', (req, res) => {
   let actions = werewolf.actions_get(),
       bigvote = actions.bigvote?actions.bigvote[0]:null;
   send_json(res, {bigvote});
});

app.post('/api/werewolf/night', (req, res) => {
   let actions = werewolf.actions_get(),
       died = actions.kill || [],
       i, heal, protect;
   if (actions.heal) {
      heal = actions.heal[0];
      i = died.indexOf(heal);
      if (i >= 0) died.splice(i, 1);
   }
   if (actions.protect) {
      protect = actions.protect[0];
      i = died.indexOf(protect);
      if (heal === protect) died.push(heal);
      else if (i >= 0) died.splice(i, 1);
   }
   if (actions.poison) died = died.concat(actions.poison);
   if (actions.lovers) {
      let love_die = [];
      died.forEach((x) => {
         i = actions.lovers.indexOf(x);
         if (i === 0 && died.indexOf(actions.lovers[1]) < 0) love_die.push(actions.lovers[1]);
         else if (i === 1 && died.indexOf(actions.lovers[0]) < 0) love_die.push(actions.lovers[0]);
      });
      died = died.concat(love_die);
   }
   let m;
   if (died.length) {
      m = '昨晚死亡的人员有 ' + died.map((x) => werewolf.player_get_obj(x).name).join(', ');
   } else {
      m = '昨晚平安夜;';
   }
   werewolf.state_set('-');
   send_json(res, {info: m});
});

app.use('/', express.static(static_dir));

app.listen(9090, '0.0.0.0', () => {
   console.log(`Werewolf is listening at 0.0.0.0:9090`);
});
