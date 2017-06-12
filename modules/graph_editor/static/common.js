'use strict';

function dom(id) {
  return document.getElementById(id);
}

function uriencode(data) {
   if (!data) return data;
   return '?' + Object.keys(data).map(function (x) {
      return (encodeURIComponent(x) + '=' + encodeURIComponent(data[x]))}).join('&');
}

function ajax (options, done_fn, fail_fn) {
  var xhr = new XMLHttpRequest(),
      payload = null;
  xhr.open(options.method || 'POST', options.url + (options.data?uriencode(options.data):''), true);
  xhr.addEventListener('readystatechange', function (evt) {
     if (evt.target.readyState === 4 /*XMLHttpRequest.DONE*/) {
        if (~~(evt.target.status/100) === 2) {
           done_fn && done_fn(JSON.parse(evt.target.response || 'null'));
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

function green_border(element) {
   element.style.border = '1px solid green';
}
function red_border(element) {
   element.style.border = '1px solid red';
}

function clear_element(element) {
   while (element.hasChildNodes()) {
       element.removeChild(element.lastChild);
   }
}

function ip_encode(ip) {
   return ip.split('.').join('-');
}
