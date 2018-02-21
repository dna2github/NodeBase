package seven.drawalive.nodebase;

import android.util.Log;

import java.util.ArrayList;

public class StringUtils {
   public static String[] parseArgv(String argv) {
      ArrayList<String> r = new ArrayList<>();
      if (argv == null) return null;
      StringBuffer buf = new StringBuffer();
      int state = 0;
      char last = ' ';
      for(char ch : argv.toCharArray()) {
         switch (state) {
            case 0:
               if (Character.isSpaceChar(ch)) {
                  if (Character.isSpaceChar(last)) {
                     break;
                  }
                  if (buf.length() > 0) {
                     r.add(new String(buf));
                     buf = new StringBuffer();
                  }
                  last = ch;
                  break;
               } else if (ch == '"') {
                  state = 1;
               } else if (ch == '\'') {
                  state = 2;
               } else if (ch == '\\') {
                  state += 90;
               }
               buf.append(ch);
               last = ch;
               break;
            case 1:
               buf.append(ch);
               if (ch == '"' && last != '\\') {
                  last = ch;
                  state = 0;
                  break;
               }
               last = ch;
               break;
            case 2:
               buf.append(ch);
               if (ch == '\'' && last != '\\') {
                  last = ch;
                  state = 0;
                  break;
               }
               last = ch;
               break;
            case 90:
            case 91:
            case 92:
               buf.append(ch);
               last = ch;
               state -= 90;
               break;
         }
      }
      if (buf.length() > 0) {
         r.add(new String(buf));
      }
      String[] parsed = new String[r.size()];
      r.toArray(parsed);
      return parsed;
   }
}
