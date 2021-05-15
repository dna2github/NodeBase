package net.seven.nodebase

import androidx.annotation.NonNull
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Handler

import java.io.File

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.EventChannel

class MainActivity: FlutterActivity() {
  private val BATTERY_CHANNEL = "net.seven.nodebase/battery"
  private val APP_CHANNEL = "net.seven.nodebase/app"
  private val NODEBASE_CHANNEL = "net.seven.nodebase/nodebase"
  private val EVENT_CHANNEL = "net.seven.nodebase/event"
  private val eventHandler = NodeBaseEventHandler()
  private val NodeBaseServiceMap = mutableMapOf<String, NodeMonitor>()

  override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
    super.configureFlutterEngine(flutterEngine)

    // Note: MethodCallHandler is invoked on the main thread.
    MethodChannel(flutterEngine.dartExecutor.binaryMessenger, BATTERY_CHANNEL).setMethodCallHandler {
      call, result ->
      if (call.method == "getBatteryLevel") {
        val batteryLevel = getBatteryLevel()

        if (batteryLevel != -1) {
          result.success(batteryLevel)
        } else {
          result.error("UNAVAILABLE", "Battery level not available.", null)
        }
      } else {
        result.notImplemented()
      }
    }

    MethodChannel(flutterEngine.dartExecutor.binaryMessenger, APP_CHANNEL).setMethodCallHandler {
      call, result ->
      if (call.method == "RequestExternalStoragePermission") {
        result.success(requestExternalStoragePermission())
      } else if (call.method == "KeepScreenOn") {
        var sw: Boolean? = call.argument("sw")
        if (sw == true) {
          keepScreenOn(true)
        } else {
          keepScreenOn(false)
        }
        result.success(0)
      } else if (call.method == "FetchExecutable") {
        var src: String? = call.argument("url")
        var dst: String? = call.argument("target")
        if (src == null || dst == null) {
          result.error("INVALID_PARAMS", "invalid parameter.", null)
        } else {
          val file = File(dst)
          val dir = file.getParentFile()
          if (!dir.exists()) {
            Storage.makeDirectory(dir.getAbsolutePath())
          }
          result.success(fetchAndMarkExecutable(src, dst))
        }
      } else if (call.method == "FetchApp") {
        var src: String? = call.argument("url")
        var dst: String? = call.argument("target")
        if (src == null || dst == null) {
          result.error("INVALID_PARAMS", "invalid parameter.", null)
        } else {
          val dir = File(dst)
          if (!dir.exists()) {
            Storage.makeDirectory(dir.getAbsolutePath())
          }
          result.success(fetchApp(src, dst))
        }
      } else if (call.method == "FetchWifiIpv4") {
        result.success(fetchWifiIpv4())
      } else {
        result.notImplemented()
      }
    }

    MethodChannel(flutterEngine.dartExecutor.binaryMessenger, NODEBASE_CHANNEL).setMethodCallHandler {
      call, result ->
      if (call.method == "GetStatus") {
        var app: String? = call.argument("app")
        app?.let { result.success(getAppStatus(app)) }
      } else if (call.method == "Start") {
        var app: String? = call.argument("app")
        var cmd: String? = call.argument("cmd")
        app?.let { cmd?.let { result.success(startApp(app, cmd)) } }
      } else if (call.method == "Stop") {
        var app: String? = call.argument("app")
        app?.let { result.success(stopApp(app)) }
      } else if (call.method == "Unpack") {
        var app: String? = call.argument("app")
        var zipfile: String? = call.argument("zipfile")
        var path: String? = call.argument("path")
        app?.let { zipfile?.let { path?.let {
          val dir = File(path)
          if (!dir.exists()) {
            Storage.makeDirectory(dir.getAbsolutePath())
          }
          result.success(fetchAndUnzip(zipfile, path))
        } } }
      } else if (call.method == "Pack") {
        var app: String? = call.argument("app")
        var zipfile: String? = call.argument("zipfile")
        var path: String? = call.argument("path")
        app?.let { zipfile?.let { path?.let {
          result.success(fetchAndZip(path, zipfile))
        } } }
      } else if (call.method == "Browser") {
        var url: String? = call.argument("url")
        url?.let {
          result.success(openInExternalBrowser(url))
        }
      } else {
        result.notImplemented()
      }
    }

    EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL).setStreamHandler(eventHandler)
  }

  private fun openInExternalBrowser(url: String): Boolean {
    External.openBrowser(this, url)
    return true
  }

  private fun fetchAndZip(target_dir: String, zipfile: String): Boolean {
    // TODO: wrap a thread instead of running on main thread
    return Storage.zip(target_dir, zipfile)
  }

  private fun fetchAndUnzip(zipfile: String, target_dir: String): Boolean {
    // TODO: wrap a thread instead of running on main thread
    Storage.unzip(zipfile, target_dir)
    return true
  }

  private fun getAppStatus(app: String): String {
    val m = NodeBaseServiceMap.get(app)
    if (m == null) return "n/a"
    if (m.isRunning) return "started"
    if (m.isDead) return "stopped"
    return "unknown"
  }

  private fun startApp(app: String, cmd: String): Boolean {
    val m = NodeBaseServiceMap.get(app)
    if (m != null) {
      if (!m.isDead) return true
    }
    val cmdarr = StringUtils.parseArgv(cmd)
    val exec = NodeMonitor(app, cmdarr)
    val handler = Handler()
    val evt = object: NodeMonitorEvent {
      override fun before(cmd: Array<String>) {}
      override fun started(cmd: Array<String>, process: Process) {
        handler.post(object: Runnable { override fun run() { eventHandler.send(app + "\nstart") } });
      }
      override fun error(cmd: Array<String>, process: Process) {}
      override fun after(cmd: Array<String>, process: Process) {
        handler.post(object: Runnable { override fun run() { eventHandler.send(app + "\nstop") } });
      }
    }
    exec.setEvent(evt)
    NodeBaseServiceMap[app] = exec
    exec.start()
    return true
  }

  private fun stopApp(app: String): Boolean {
    val m = NodeBaseServiceMap.get(app)
    if (m == null) return true
    if (m.isDead) return true
    m.stopService()
    NodeBaseServiceMap.remove(app)
    return true
  }

  private fun fetchWifiIpv4(): String {
    return Network.getWifiIpv4(this)
  }

  private fun _markExecutable(dst: String): Boolean {
    val isZip = dst.endsWith(".zip")
    if (isZip) {
      val f = File(dst)
      val t = f.getParentFile().getAbsolutePath()
      android.util.Log.i("NodeBase", String.format("extracting %s -> %s ...", dst, t))
      for (one in Storage.unzip(dst, t)) {
        android.util.Log.i("NodeBase", String.format("   %s", one.getAbsolutePath()))
        Storage.executablize(one.getAbsolutePath())
      }
      return Storage.unlink(dst)
    } else {
      return Storage.executablize(dst)
    }
  }

  private fun fetchAndMarkExecutable(src: String, dst: String): Int {
    if (src == "") return -1
    if (src.startsWith("file://")) {
      Permission.request(this)
      var final_src = src
      final_src = final_src.substring("file://".length)
      // Add Alarm to align with Download()
      // XXX: but how about we move Alarm out of Download() and use call back to do alarm?
      if (!Storage.copy(final_src, dst)) {
        Alarm.showToast(this, "Copy failed: cannot copy origin")
        return -2
      }
      if (!_markExecutable(dst)) {
        Alarm.showToast(this, "Copy failed: cannot set binary executable")
        return -3
      }
      Alarm.showToast(this, "Copy successful")
      return 0
    } else {
      // download
      val postAction = object : Runnable {
        override fun run() {
          _markExecutable(dst)
        }
      }
      Download(this, postAction).act("fetch", src, dst)
    }
    return 0
  }

  private fun _unpackApp(dst: String): Boolean {
    // dst is a zip file path
    val f = File(dst)
    val t = f.getParentFile().getAbsolutePath()
    android.util.Log.i("NodeBase", String.format("extracting %s -> %s ...", dst, t))
    for (one in Storage.unzip(dst, t)) {
      android.util.Log.i("NodeBase", String.format("   %s", one.getAbsolutePath()))
    }
    return Storage.unlink(dst)
  }

  private fun fetchApp(src: String, dst: String): Int {
    if (src == "") return -1
    if (!src.endsWith(".zip")) return -1
    Storage.makeDirectory(dst)
    val src_name = File(src).getName()
    var dst_zip = dst + "/" + src_name
    if (src.startsWith("file://")) {
      Permission.request(this)
      var final_src = src
      final_src = final_src.substring("file://".length)
      // Add Alarm to align with Download()
      // XXX: but how about we move Alarm out of Download() and use call back to do alarm?
      if (!Storage.copy(final_src, dst_zip)) {
        Alarm.showToast(this, "Copy failed: cannot copy origin")
        return -2
      }
      if (!_unpackApp(dst_zip)) {
        Alarm.showToast(this, "Copy failed: cannot set binary executable")
        return -3
      }
      Alarm.showToast(this, "Copy successful")
      return 0
    } else {
      // download
      val postAction = object : Runnable {
        override fun run() {
          _unpackApp(dst_zip)
        }
      }
      Download(this, postAction).act("fetch", src, dst_zip)
    }
    return 0
  }

  private fun requestExternalStoragePermission(): Int {
    Permission.request(this)
    return 0
  }

  private fun keepScreenOn(sw: Boolean) {
    Permission.keepScreen(this, sw)
  }

  private fun getBatteryLevel(): Int {
    val batteryLevel: Int
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
      batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    } else {
      val intent = ContextWrapper(applicationContext).registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
      batteryLevel = intent!!.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    }
    return batteryLevel
  }
}
