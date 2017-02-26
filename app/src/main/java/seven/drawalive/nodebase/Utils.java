package seven.drawalive.nodebase;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Utils {

   public static final int PERMISSIONS_EXTERNAL_STORAGE = 1;

   public static boolean prepareNodeDirectory(String workdir, String subdir) {
      try {
         File dir = new File(String.format("%s%s", workdir, subdir));
         if (!dir.exists()) {
            return dir.mkdirs();
         }
         return true;
      } catch (Exception e) {
         Log.w("Config:Prepare",
               String.format("Cannot create directory of \"%s%s\"", workdir, subdir));
         return false;
      }
   }

   public static boolean prepareNodeDirectories(String workdir) {
      prepareNodeDirectory(workdir, "/node");
      return true;
   }

   public static boolean prepareNode(String workdir, InputStream node_binary) {
      try {
         String node_filename = String.format("%s/node/node", workdir);
         File node_file = new File(node_filename);
         if (node_file.exists()) {
            return true;
         }
         node_file.createNewFile();
         InputStreamReader reader = new InputStreamReader(node_binary);
         FileOutputStream writer = new FileOutputStream(node_file);
         byte[] binary = new byte[(int)(node_binary.available())];
         node_binary.read(binary);
         writer.write(binary);
         writer.flush();
         writer.close();
         node_file.setExecutable(true, true);
         return true;
      } catch (Exception e) {
         Log.w("Config:Prepare",
               "Cannot create binary file of \"node\"");
         return false;
      }
   }

   private static String _serviceAuthentication = null;

   public static void setServiceAuthentication (String auth) {
      if (_serviceAuthentication == null) {
         _serviceAuthentication = auth;
         return;
      }
      synchronized (_serviceAuthentication) {
         _serviceAuthentication = auth;
      }
   }

   public static String getServiceAuthentication () {
      return _serviceAuthentication;
   }

   private static String[] _serviceInfo = null;

   public static void setServiceInfo (String[] info) {
      if (_serviceInfo == null) {
         _serviceInfo = info;
         return;
      }
      synchronized (_serviceInfo) {
         _serviceInfo = info;
      }
   }

   public static String[] getServiceInfo () {
      return _serviceInfo;
   }

   public static String getIPv4(Context context) {
      WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
      WifiInfo wifiInfo = wifiManager.getConnectionInfo();
      int ip = wifiInfo.getIpAddress();
      return String.format("%d.%d.%d.%d", ip & 0xff, (ip >> 8) & 0xff, (ip >> 16) & 0xff, (ip >> 24) & 0xff);
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

   public static String readSmallFile(String filename) {
      FileInputStream reader = null;
      File file = new File(filename);
      try {
         byte[] buf = new byte[(int) file.length()];
         reader = new FileInputStream(file);
         reader.read(buf);
         return new String(buf);
      } catch (Exception e) {
         return null;
      } finally {
         if (reader != null) {
            try { reader.close(); } catch (Exception e) {}
         }
      }
   }

   public static void resetNodeJS(Context context, String workdir) {
      prepareNodeDirectories(workdir);

      InputStream node = null;
      try {
         node = context.getResources().openRawResource(R.raw.bin_node_v710);
         prepareNode(workdir, node);
         node.close();
      } catch (Exception e) {
         Log.e("Server:Prepare",
               "Cannot create binary file of \"node\"");
      }
   }
}
