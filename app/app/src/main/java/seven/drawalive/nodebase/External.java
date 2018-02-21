package seven.drawalive.nodebase;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AlertDialog;
import android.widget.Toast;

import java.io.File;

public class External {
   public static void openBrowser(Context context, String url) {
      Intent intent = new Intent(Intent.ACTION_SEND);
      intent.setAction("android.intent.action.VIEW");
      intent.setData(Uri.parse(url));
      context.startActivity(intent);
   }

   public static void shareInformation(
         Context context, String title,
         String label, String text, String imgFilePath) {
      Intent intent = new Intent(Intent.ACTION_SEND);
      if (imgFilePath == null || imgFilePath.equals("")) {
         intent.setType("text/plain");
      } else {
         File f = new File(imgFilePath);
         if (f != null && f.exists() && f.isFile()) {
            intent.setType("image/jpg");
            Uri u = Uri.fromFile(f);
            intent.putExtra(Intent.EXTRA_STREAM, u);
         }
      }
      intent.putExtra(Intent.EXTRA_SUBJECT, label);
      intent.putExtra(Intent.EXTRA_TEXT, text);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      context.startActivity(Intent.createChooser(intent, title));
   }

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
