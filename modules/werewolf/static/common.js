'use strict';
function $(id){
  var el = 'string' == typeof id
    ? document.getElementById(id)
    : id;

  el.on = function(event, fn){
    if ('content loaded' == event) {
      event = window.attachEvent ? "load" : "DOMContentLoaded";
    }
    el.addEventListener
      ? el.addEventListener(event, fn, false)
      : el.attachEvent("on" + event, fn);
  };

  el.all = function(selector){
    return $(el.querySelectorAll(selector));
  };

  el.each = function(fn){
    for (var i = 0, len = el.length; i < len; ++i) {
      fn($(el[i]), i);
    }
  };

  el.getClasses = function(){
    return this.getAttribute('class').split(/\s+/);
  };

  el.addClass = function(name){
    var classes = this.getAttribute('class');
    el.setAttribute('class', classes
      ? classes + ' ' + name
      : name);
  };

  el.removeClass = function(name){
    var classes = this.getClasses().filter(function(curr){
      return curr != name;
    });
    this.setAttribute('class', classes.join(' '));
  };

  el.prepend = function (child) {
    this.insertBefore(child, this.firstChild);
  };

  el.append = function (child) {
    this.appendChild(child);
  };

  el.css = function (name, value) {
    this.style[name] = value;
  }

  el.click = function () {
    var event = new MouseEvent('click', {
      view: window,
      bubbles: false,
      cancelable: true
    });
    this.dispatchEvent(event);
  };

  return el;
}

function uriencode(data) {
   if (!data) return data;
   return '?' + Object.keys(data).map(function (x) {
      return (encodeURIComponent(x) + '=' + encodeURIComponent(data[x]))}).join('&');
}

function ajax (options, done_fn, fail_fn) {
  var xhr = new XMLHttpRequest();
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
  xhr.send(null);
}

function green_border(element) {
   element.style.border = '1px solid green';
}
function red_border(element) {
   element.style.border = '1px solid red';
}
function clear_border(element) {
   element.style.border = null;
}

function clear_element(element) {
   while (element.hasChildNodes()) {
       element.removeChild(element.lastChild);
   }
}

function generate_players(allow_none, players, sel_element) {
   clear_element(sel_element);
   if (allow_none) {
      var c = document.createElement('option');
      c.value = '';
      c.appendChild(document.createTextNode('<弃权>'));
      sel_element.appendChild(c);
   }
   if (players) {
      players.forEach(function (x) {
         if (!x) return;
         var c = document.createElement('option');
         c.value = x.ip;
         c.appendChild(document.createTextNode(x.name));
         sel_element.appendChild(c);
      });
   }
}

function play_sound(element, soundfile) {
   // element.innerHTML = '<audio src="' + soundfile +'" autoplay="true" />';
   element.play();
}

function ip_encode(ip) {
   return ip.split('.').join('-');
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
