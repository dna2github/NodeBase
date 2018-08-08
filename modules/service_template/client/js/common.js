'use strict';

function dom(selector) {
   return document.querySelector(selector);
}

function ajax (options, done_fn, fail_fn) {
  var xhr = new XMLHttpRequest(),
      payload = null;
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
}

function html (url, done_fn, fail_fn) {
  var xhr = new XMLHttpRequest(),
      payload = null;
  xhr.open('GET', url, true);
  xhr.addEventListener('readystatechange', function (evt) {
     if (evt.target.readyState === 4 /*XMLHttpRequest.DONE*/) {
        if (~~(evt.target.status/100) === 2) {
           done_fn && done_fn(evt.target.response || '<!-- empty -->');
        } else {
           fail_fn && fail_fn(evt.target.status);
        }
     }
  });
  xhr.send(null);
}

function get_cookie() {
   var items = document.cookie;
   var r = {};
   if (!items) return r;
   items.split(';').forEach(function (one) {
      var p = one.indexOf('=');
      if (p < 0) r[one.trim()] = null;
            else r[one.substring(0, p).trim()] = one.substring(p+1).trim();
   });
   return r;
}

function set_cookie(key, value) {
   document.cookie = key + '=' + escape(value) + ';domain=example.localhost';
}
