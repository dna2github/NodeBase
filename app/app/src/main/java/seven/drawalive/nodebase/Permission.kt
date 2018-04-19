package seven.drawalive.nodebase

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat

object Permission {

    private var power_wake_lock: PowerManager.WakeLock? = null
    private var PERMISSIONS_EXTERNAL_STORAGE = 1
    fun request(activity: Activity) {
        val permission: Int
        permission = ContextCompat.checkSelfPermission(
                activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
                    PERMISSIONS_EXTERNAL_STORAGE)
        }
    }

    fun keepScreen(activity: Activity, on: Boolean) {
        val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (power_wake_lock == null) {
            power_wake_lock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, Permission::class.java!!.getName()
            )
        }
        if (on) {
            power_wake_lock!!.acquire()
        } else {
            power_wake_lock!!.release()
        }
    }
}
