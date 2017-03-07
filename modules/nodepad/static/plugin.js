'use strict';

function plugin () {

   var modules = {},
       env = {};

   function __noop__() {}

   function __unload__(name, callback) {
      var obj = modules[name];
      if (!obj) return false;
      document.body.removeChild(obj.$script);
      obj.$script = null;
      if (callback) callback(name, env);
      return true;
   }

   function __load__(name, uri, callback, force) {
      var obj = modules[name];
      if (obj && force !== true) return false;
      if (obj && !__unload__(name)) return false;
      obj = {
         name: name,
         uri: uri,
         $script: document.createElement('script')
      };
      obj.$script.type = 'text/javascript';
      obj.$script.src = uri;
      obj.$script.addEventListener('load', __factory_onload__(obj, callback));
      document.body.appendChild(obj.$script);
      return true;
   }

   function __factory_onload__(obj, callback) {
      return function (evt) {
         if (callback) callback(obj.name, env);
      }
   }

   return {
      load: __load__,
      unload: __unload__,
      get_modules: function () { return modules; },
      get_env: function () { return env; }
   };

}
