package seven.drawalive.nodebase;

import android.os.Handler;
import android.os.Looper;
import android.widget.Button;

public class UserInterface {
   public static void run(Runnable runnable) {
      new Handler(Looper.getMainLooper()).post(runnable);
   }

   public static void themeAppTitleButton(Button button, boolean running) {
      if (running) {
         // light green
         button.setBackgroundColor(0xffcff5cd);
      } else {
         // light grey
         button.setBackgroundColor(0xffe2e2e2);
      }
   }
}
