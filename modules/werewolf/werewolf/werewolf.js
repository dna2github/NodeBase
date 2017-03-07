const uuid = require('uuid');

let players = { /* ip: obj */ };
let players_order = [ /* ip */ ];
let actions = {/*
   lovers, kill, heal, poison, protect, bigvote, admire, see, deal,
   infect, theif
*/};
let state = {
   cur: ' ',
   config: null,
   id: uuid.v4()
};

function player_find(key, value) {
   for (var ip in players) {
      if (players[ip][key] === value) return ip;
   }
   return null;
}

function player_all() {
   return players;
}

function player_get_obj(ip) {
   return players[ip];
}

function player_register(ip, name, role) {
   let p = players[ip];
   if (!p) {
      p = { ip, name, alive: true, init_role: role, role: role };
      players[ip] = p;
      players_order.push(ip);
   } else {
      p.name = name;
      p.init_role = role;
      p.role = role;
   }
   return p;
}

function player_unregister(ip) {
   delete players[ip];
   if (players_order) {
      let index = players_order.indexOf(ip);
      if (index >= 0) players_order.splice(index, 1);
   }
}

function player_reorder(seq) {
   players_order = seq;
}

function player_get_order() {
   return players_order;
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
      m += (actions.poison?('女巫毒药使用给了 ' + players[actions.poison[0]].name +';'):'');
      m += (actions.heal?('女巫解药使用给了 ' + players[actions.heal[0]].name +';'):'');
      m += (actions.admire?('野孩子崇拜了 ' + players[actions.admire[0]].name + ';'):'');
      m += ((actions.kill && actions.kill.length > 0)?('狼人猎杀了 ' + players[actions.kill[0]].name + ';'):'');
      m += (actions.bigvote?('乌鸦把增加票的权利给了 ' + players[actions.kill[0]].name + ';'):'');
      m += (actions.deal?('两姐妹协商投票给 ' + players[actions.deal[0]].name + ';'):'');
      m += (actions.lovers?(players[actions.lovers[0]].name + ' 和 ' + players[actions.lovers[1]].name +' 是情侣;'):'');
      m += '[ ';
      Object.keys(players).forEach((x) => {
         m += players[x].name + ' 是 ' + player_role_name(players[x].init_role) + '; ';
      });
      m += ']'
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
   if (s.cur === 'T') {
      s.i_roles = [];
      if (state.config) {
         let roles = Object.assign({}, state.config);
         for(let ip in players) {
            roles[players[ip].init_role] --;
         }
         for(let role in roles) {
            if (roles[role] > 0) s.i_roles.push({
               ip: role,
               name: player_role_name(role)
            });
         }
      }
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
         p.alive = true;
         delete p.init_role;
         delete p.role;
      });
      break;
   default:
   }
   state.cur = val;
}

function config_get() {
   return state.config;
}

function config_set(config) {
   state.config = config;
}

function actions_get() {
   return actions;
}

function actions_set(key, value) {
   if (!value) {
      delete actions[key];
      return;
   }
   // e.g. 1,2,3 => [1,2,3]
   // e.g. +1,2  => [x,y] + [1,2] = [x,y,1,2]
   value = value.split(',');
   if (value[0].charAt(0) === '+') {
      value[0] = value[0].substring(1);
      if (!actions[key]) actions[key] = [];
      actions[key] = actions[key].concat(value);
   } else {
      actions[key] = value;
   }
   if (key === 'role') {
      let ip = player_find('init_role', 'T');
      players[ip].role = value[0];
   }
}

function night_result() {
   let died = actions.kill || [],
       i, heal, protect;
   if (actions.heal) {
      heal = actions.heal[0];
      i = died.indexOf(heal);
      if (i >= 0) died.splice(i, 1);
   }
   if (actions.protect) {
      protect = actions.protect[0];
      i = died.indexOf(protect);
      if (heal === protect) {
         died.push(heal);
      } else if (i >= 0) {
         died.splice(i, 1);
      }
   }
   if (actions.poison) {
      died = died.concat(actions.poison);
   }
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
      m = '昨晚死亡的人员有 ' + died.map((x) => players[x].name).join(', ') + ';';
      died.forEach((x) => {
         let p = players[x];
         p.alive = false;
      });
      actions.died = died;
   } else {
      m = '昨晚平安夜;';
      actions.died = [];
   }
   return m;
}

function next_player(ip) {
   var i = players_order.indexOf(ip),
       j = i + 1;
   while (j !== i) {
      if (j >= players_order.length) j = 0;
      if (players[players_order[j]].alive === true) {
         return players_order[j];
      }
      j ++;
   }
   return ip;
}

function prev_player(ip) {
   var i = players_order.indexOf(ip),
       j = i - 1;
   while (j !== i) {
      if (j < 0) j = players_order.length - 1;
      if (players[players_order[j]].alive === true) {
         return players_order[j];
      }
      j --;
   }
   return ip;
}

function is_werewolf(role) {
   return ['x', 'i', 'g', 's'].indexOf(role) >= 0;
}

function info_hunter() {
   let hunter_ip = player_find('init_role', 'H'), m;
   if (!hunter_ip) return '';
   // @require: night_result
   if (actions.died.indexOf(hunter_ip)) {
      m = '猎人' + ((~~(Math.random()*2))?'可以':'不能') + '发动技能;';
   } else {
      if (actions.poison && actions.poison.indexOf(hunter_ip) >= 0) {
         m = '猎人不能发动技能;';
      } else if (actions.infect && actions.infect.indexOf(hunter_ip) >= 0) {
         m = '猎人不能发动技能;';
      } else if (actions.heal && actions.heal.indexOf(hunter_ip) >= 0) {
         // heal but die, should heal == protect
         m = '猎人不能发动技能;';
      } else if (actions.kill && actions.kill.indexOf(hunter_ip) >= 0) {
         m = '猎人可以发动技能;';
      } else if (actions.kill2 && actions.kill2.indexOf(hunter_ip) >= 0) {
         m = '猎人可以发动技能;';
      } else {
         // should die for love
         m += '猎人不能发动技能;';
      }
   }
   return m;
}

function info_bear() {
   let bear_ip = player_find('init_role', 'B');
   if (!bear_ip) return '';
   let next_ip = next_player(bear_ip),
       prev_ip = prev_player(bear_ip),
       pbear = players[bear_ip],
       pnext = players[next_ip],
       pprev = players[prev_ip];
   if (pbear.alive) {
      if (pbear.role === 'x') {
         return '熊在咆哮;';
      } else if (is_werewolf(pnext.role) || is_werewolf(pprev.role)) {
         return '熊在咆哮;';
      } else {
         return '熊很安静;';
      }
   } else {
      return '熊很安静;';
   }
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
   player_find,
   player_get_obj,
   player_register,
   player_unregister,
   player_get_info,
   player_reorder,
   player_get_order,
   actions_get,
   actions_set,
   state_get,
   state_set,
   config_get,
   config_set,
   night_result,
   info_hunter,
   info_bear
};
