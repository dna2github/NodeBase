package seven.drawalive.nodebase;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import seven.drawalive.nodebase.old.Utils;

public class Permission {
   protected static void request(Activity activity) {
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
}
