const i_ldap = require('ldapjs');
const i_uuid = require('uuid');
const i_keyval = require('./keyval');

const LDAP_SERVER = process.env.EXAMPLE_SERVICE_LDAP_SERVER || 'ldap://example.localhost:3268'

const api = {
   authenticate: (username, password) => {
      return new Promise((resolve, reject) => {
         let client = i_ldap.createClient({
            url: LDAP_SERVER
         });
         client.bind(username + '@example.localhost', password, (error) => {
            client.unbind();
            if (error) {
               reject({username, error});
            } else {
               let keys = i_keyval.keys(`auth.${username}.*`);
               Object.keys(keys).forEach((key) => {
                  i_keyval.set(key, null);
               });
               let meta = {
                  login: new Date().getTime()
               };
               let uuid = i_uuid.v4();
               i_keyval.set(keyval_authkey(username, uuid), meta);
               resolve({username, uuid});
            }
         });
      });
   },
   check_login: (username, uuid) => {
      let meta = i_keyval.get(keyval_authkey(username, uuid));
      if (!meta) return null;
      return meta;
   },
   clear: (username, uuid) => {
      return i_keyval.set(keyval_authkey(username, uuid));
   }
};

function keyval_authkey(username, uuid) {
   return `auth.${username}.${uuid}`;
}

module.exports = api;
