const i_ws = require('ws');
const i_auth = require('./auth');

const api = {
   send_error: (ws, code, text) => {
      ws.send(JSON.stringify({error: text, code: code}));
   },
   send: (ws, json) => {
      ws.send(JSON.stringify(json));
   },
   start_query: (ws, query, env) => {},
   stop_query: (ws, query, env) => {}
};

const service = {
   server: null,
   init: (server, path) => {
      service.server = new i_ws.Server({ server, path });
      service.server.on('connection', service.client);
   },
   client: (ws, req) => {
      let env = {
         authenticated: false,
         username: null,
         uuid: null,
         query: null,
         query_tasks: []
      };
      setTimeout(() => {
         // if no login in 5s, close connection
         if (!env.authenticated) {
            ws.close();
         }
      }, 5000);
      ws.on('message', (m) => {
         try {
            m = JSON.parse(m);
         } catch(e) {
            api.send_error(400, 'Bad Request');
            return;
         }
         if (m.cmd === 'auth') {
            if (!i_auth.check_login(m.username, m.uuid)) {
               api.send_error(401, 'Not Authenticated');
               return;
            }
            env.authenticated = true;
            env.username = m.username;
            env.uuid = m.uuid;
            return;
         }
         if (!env.authenticated) {
            api.send_error(401, 'Not Authenticated');
            return;
         }
         switch (m.cmd) {
         };
      });
      ws.on('close', () => {
      });
      ws.on('error', (error) => {
      });
   }
};

module.exports = service;
