package net.seven.nodebase

import io.flutter.plugin.common.EventChannel

class NodeBaseEventHandler() : EventChannel.StreamHandler {
   var _sink: EventChannel.EventSink? = null

   override fun onListen(p0: Any?, p1: EventChannel.EventSink?) {
     _sink = p1
   }

   override fun onCancel(p0: Any?) {
      _sink = null
   }

   fun send(text: String) {
      _sink?.success(text)
   }
}
