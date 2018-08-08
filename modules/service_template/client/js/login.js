'use strict';
//@include common.js

var ui = {
   login: {
      type: dom('#login-auth-source-1'),
      username: dom('#login_username'),
      password: dom('#login_password'),
      error: dom('#login_error'),
      signin: dom('#btn_signin'),
   }
};

(function init(){
   var cookie = get_cookie();
   if (cookie.service_username) {
      ui.login.username.value = cookie.service_username;
      ui.login.password.focus();
   } else {
      ui.login.username.focus();
   }
   ui.login.error.style.display = 'none';
})();

ui.login.signin.addEventListener('click', function (evt) {
   var username = ui.login.username.value;
   var password = ui.login.password.value;
   ui.login.error.style.display = 'none';
   ajax({
      url: '/api/auth/login',
      json: {
         username: username,
         password: password
      }
   }, function (response) {
      response = JSON.parse(response);
      set_cookie('service_username', username);
      set_cookie('service_uuid', response.uuid);
      window.location = '/';
   }, function (status) {
      ui.login.error.style.display = undefined;
      ui.login.password.focus();
   });
});
