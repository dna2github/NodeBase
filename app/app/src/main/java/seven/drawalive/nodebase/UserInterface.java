package seven.drawalive.nodebase;

import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;

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

   public static final LinearLayout.LayoutParams buttonLeftStyle = new LinearLayout.LayoutParams(
         LinearLayout.LayoutParams.MATCH_PARENT,
         LinearLayout.LayoutParams.WRAP_CONTENT
   );
   public static final LinearLayout.LayoutParams buttonRightStyle = new LinearLayout.LayoutParams(
         LinearLayout.LayoutParams.MATCH_PARENT,
         LinearLayout.LayoutParams.WRAP_CONTENT
   );
   public static final LinearLayout.LayoutParams buttonFillStyle = new LinearLayout.LayoutParams(
         LinearLayout.LayoutParams.MATCH_PARENT,
         LinearLayout.LayoutParams.WRAP_CONTENT
   );

   static {
      buttonLeftStyle.setMargins(0, 0, 10, 3);
      buttonRightStyle.setMargins(10, 0, 0, 3);
      buttonFillStyle.setMargins(0, 0, 0, 0);
   }
}
