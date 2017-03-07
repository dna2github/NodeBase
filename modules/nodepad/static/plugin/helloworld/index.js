'use strict';

(function () {

html('/plugin/helloworld/index.html', function (data) {
   green_border(document.getElementById('btn_plugin_run'));
   clear_element(document.getElementById('view_plugin'));
   document.getElementById('view_plugin').innerHTML = data;
   $('plugin_helloworld_btn_hello').on('click', function (evt) {
      document.getElementById('plugin_helloworld_text').value = 'World';
   });
}, function () {
   red_border(document.getElementById('btn_plugin_run'));
});

})();
