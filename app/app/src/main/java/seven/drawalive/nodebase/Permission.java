package seven.drawalive.nodebase;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import seven.drawalive.nodebase.old.Utils;

public class Permission {
   public static void request(Activity activity) {
      int permission;
      permission = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
      if (permission != PackageManager.PERMISSION_GRANTED) {
         ActivityCompat.requestPermissions(
               activity,
               new String[] {
                     Manifest.permission.WRITE_EXTERNAL_STORAGE,
                     Manifest.permission.READ_EXTERNAL_STORAGE
               },
               Utils.PERMISSIONS_EXTERNAL_STORAGE);
      }
   }

   private static PowerManager.WakeLock power_wake_lock = null;
   public static void keepScreen(Activity activity, boolean on) {
      PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
      if (power_wake_lock == null) {
         power_wake_lock = pm.newWakeLock(
               PowerManager.PARTIAL_WAKE_LOCK, Permission.class.getName()
         );
      }
      if (on) {
         power_wake_lock.acquire();
      } else {
         power_wake_lock.release();
      }
   }
}
