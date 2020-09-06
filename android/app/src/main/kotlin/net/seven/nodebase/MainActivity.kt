package net.seven.nodebase

import androidx.annotation.NonNull
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES

import java.io.File

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
  private val BATTERY_CHANNEL = "net.seven.nodebase/battery"
  private val APP_CHANNEL = "net.seven.nodebase/app"
  private val NODEBASE_CHANNEL = "net.seven.nodebase/nodebase"

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
      } else {
        result.notImplemented()
      }
    }
  }

  private fun fetchAndMarkExecutable(src: String, dst: String): Int {
    if (src == null) return -1
    if (src.startsWith("file://")) {
      Permission.request(this)
      var final_src = src
      final_src = final_src.substring("file://".length)
      if (!Storage.copy(final_src, dst)) return -2
      if (!Storage.executablize(dst)) return -3
      return 0
    } else {
      // download
      Download(this, Runnable() {
        fun run() {
          Storage.executablize(dst)
        }
      }).act("fetch", src, dst)
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
