'use strict';
//@include common.js

var ui = {
   loading: dom('#p_loading'),
   app: {
      self: dom('#p_app')
   }
};

var utils = {
   loading: {
      show: function () { ui.loading.classList.remove('hide'); },
      hide: function () { ui.loading.classList.add('hide'); }
   }
};

var env = {};

(function init () {
   utils.loading.show();
   ui.app.self.classList.add('hide');
   var cookie = get_cookie();
   env.user = {
      username: cookie.service_username,
      uuid: cookie.service_uuid
   };
   if (!env.user.username || !env.user.uuid) {
      window.location = '/login.html';
      return;
   }
   ajax({
      url: '/api/auth/check',
      json: {
         username: env.user.username,
         uuid: env.user.uuid
      }
   }, function () {
      utils.loading.hide();
      ui.app.self.classList.remove('hide');;
      init_app();
   }, function () {
      window.location = '/login.html';
   });
})();

function init_app() {
}
