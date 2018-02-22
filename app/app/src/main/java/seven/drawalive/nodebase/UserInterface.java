package seven.drawalive.nodebase;

import android.os.Handler;
import android.os.Looper;

public class UserInterface {
   public static void run(Runnable runnable) {
      new Handler(Looper.getMainLooper()).post(runnable);
   }
}
