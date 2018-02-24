package seven.drawalive.nodebase;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class ModuleAppManager {
   public static String js(Context context) {
      InputStream reader = context.getResources().openRawResource(R.raw.app_manager);
      try {
         byte[] buf = new byte[(int) reader.available()];
         reader.read(buf);
         return new String(buf);
      } catch (IOException e) {
         return null;
      } finally {
         if (reader != null) try { reader.close(); } catch (Exception e) {}
      }
   }

   public static String readme() {
      return "# NodeBase Application Manager\nrunning: 20180\nparams:  (no params)\n";
   }

   public static String config() {
      return "name=NodeBase Application Manager\nport=20180\n";
   }

   public static void install(Context context, String workdir) {
      String appdir = workdir + "/app_manager";
      File dir = new File(appdir);
      if (dir.exists()) {
         return;
      }
      dir.mkdir();
      Storage.write(js(context), appdir + "/index.js");
      Storage.write(readme(), appdir + "/readme");
      Storage.write(config(), appdir + "/config");
   }
}
