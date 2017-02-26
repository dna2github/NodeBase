const uuid = require('uuid');

let players = { /* ip: obj */ };
let players_order = [ /* ip */ ];
let actions = {/*
   lovers: [], kill, heal, poison, protect, bigvote, admire, see, deal
*/};
let state = {
   cur: ' ',
   id: uuid.v4()
};

function player_all() {
   return players;
}

function player_get_obj(ip) {
   return players[ip];
}

function player_register(ip, name) {
   let p = players[ip];
   if (!p) {
      p = { ip, name, alive: true, init_role: ' ', real_role: ' ' };
      players[ip] = p;
   } else {
      p.name = name;
   }
   return p;
}

function player_unregister(ip) {
   delete players[ip];
}

function player_get_info(ip) {
   let p = players[ip];
   let m = '';
   if (!p) return '请问你哪位？';
   if (actions.lovers) {
      if (actions.lovers.indexOf(ip) >= 0) {
         m += players[actions.lovers[0]].name + ' 和 ' + players[actions.lovers[1]].name +' 是情侣;';
      }
   }
   if (!p.alive) {
      m += (actions.poison?('女巫毒药使用给了 ' + players[actions.poison[0]].name +';'):'') +
           (actions.heal?('女巫解药使用给了 ' + players[actions.heal[0]].name +';'):'') +
           (actions.admire?('野孩子崇拜了 ' + players[actions.admire[0]].name + ';'):'') +
           ((actions.kill && actions.kill.length > 0)?('狼人猎杀了 ' + players[actions.kill[0]].name + ';'):'') +
           (actions.bigvote?('乌鸦把增加票的权利给了 ' + players[actions.kill[0]].name + ';'):'') +
           (actions.deal?('两姐妹协商投票给 ' + players[actions.deal[0]].name + ';'):'') +
           (actions.lovers?(players[actions.lovers[0]].name + ' 和 ' + players[actions.lovers[1]].name +' 是情侣;'):'');
   }
   return m;
}

function state_get() {
   let s = Object.assign({}, state);
   if (['x', 'S', 'W', 'U', 'Q', 'C', 'G', 'D'].indexOf(s.cur) >= 0) {
      s.i_alive = Object.keys(players).map((x) => players[x].alive?players[x]:null);
   }
   if (s.cur === 'W' && !actions.heal && actions.kill) {
      s.i_healable = actions.kill.map((x) => x?players[x]:null);
   }
   return s;
}

function state_set(val) {
   switch (val) {
   case ' ':
      state.id = uuid.v4();
      actions = {};
      Object.keys(players).forEach((p) => {
         if (!p) return;
         p = players[p];
         if (!p) return;
         delete p.role
      });
      break;
   default:
   }
   state.cur = val;
}

function actions_get() {
   return actions;
}

function actions_set(key, value) {
   if (!value) {
      delete actions[key];
      return;
   }
   value = value.split(',');
   actions[key] = value;
}

function night_result() {
}

function vote_result(vote) {
}

function player_role_name(role) {
   switch (role) {
   case 'S':
      return '预言家';
   case 'W':
      return '女巫';
   case 'H':
      return '猎人';
   case 'F':
      return '白痴';
   case 'G':
      return '守卫';
   case 'B':
      return '熊';
   case 'U':
      return '野孩子';
   case 'O':
      return '长老';
   case 'C':
      return '乌鸦';
   case 'P':
      return '锈剑骑士';
   case 'Q':
      return '丘比特';
   case 'T':
      return '盗贼';
   case 'E':
      return '替罪羊';
   case 'f':
      return '吹笛者';
   case 's':
      return '贪睡狼';
   case 'g':
      return '大野狼';
   case 'i':
      return '祖狼';
   case 'x':
      return '狼人';
   case 'o':
   default:
      return '村民';
   }
}

module.exports = {
   player_all,
   player_get_obj,
   player_register,
   player_unregister,
   player_get_info,
   actions_get,
   actions_set,
   state_get,
   state_set,
   night_result,
   vote_result
};
