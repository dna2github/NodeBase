package seven.drawalive.nodebase;

import android.content.Context;
import android.support.v7.app.AlertDialog;
import android.widget.Toast;

public class Alarm {
   public static void showMessage(Context context, String text, String title) {
      AlertDialog.Builder builder = new AlertDialog.Builder(context);
      builder.setMessage(text);
      if (title != null) builder.setTitle(title);
      builder.create().show();
   }
   public static void showMessage(Context context, String text) {
      showMessage(context, text, null);
   }

   public static void showToast(Context context, String text) {
      Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_SHORT).show();
   }
}
