package seven.drawalive.nodebase;

import android.content.Context;

import java.util.HashMap;


public class Configuration {

   public static final String NODE_URL = "https://raw.githubusercontent.com/wiki/dna2github/NodeBase/binary/v0/node";
   public static final String KEYVAL_NODEBASE_DIR = "nodebase_dir";

   public static HashMap<String, String> parse(String text) {
      if (text == null) return null;
      HashMap<String, String> keyval = new HashMap<>();
      String[] lines = text.split("\n");
      String key, val;
      for (int i = 0, n = lines.length; i < n; i++) {
         key = lines[i].trim();
         if (key.length() == 0) continue;
         i ++;
         if (i >= n) break;
         char last = key.charAt(key.length()-1);
         boolean multiple_line = false;
         if (last == '+') {
            key = key.substring(0, key.length()-1).trim();
            multiple_line = true;
         }
         if (key.length() == 0) /* after all comments */ break;
         if (multiple_line) {
            val = "";
            for (int j = i; j < n; j++) {
               val += "\n" + lines[j];
            }
            i = n;
         } else {
            val = lines[i].trim();
         }
         keyval.put(key, val);
      }
      return keyval;
   }

   public Configuration(Context context) {
      datadir = context.getApplicationInfo().dataDir;
      firstrun = false;
      load();
   }

   public void load() {
      String infile = String.format("%s/config", datadir);
      keyval = parse(Storage.read(infile));
      if (keyval == null) {
         firstrun = true;
         keyval = new HashMap<>();
      }
      if (!keyval.containsKey(KEYVAL_NODEBASE_DIR)) {
         keyval.put(KEYVAL_NODEBASE_DIR, "/sdcard/.nodebase");
      }
   }

   public void save() {
      String outfile = String.format("%s/config", datadir);
      StringBuffer buf = new StringBuffer();
      String val;
      for (String key : keyval.keySet()) {
         buf.append(key);
         buf.append('\n');
         buf.append("   ");
         val = keyval.get(key);
         if (val == null) val = "";
         if (val.indexOf('\n') >= 0) {
            val = val.replaceAll("\n", "  ");
         }
         buf.append(val);
      }
      Storage.write(new String(buf), outfile);
   }

   public String dataDir() {
      return datadir;
   }

   public String workDir() {
      return keyval.get(KEYVAL_NODEBASE_DIR);
   }

   public String nodeBin() {
      return String.format("%s/node/node", datadir);
   }

   public boolean firstRun() {
      return firstrun;
   }

   public void prepareEnvironment() {
      Storage.makeDirectory(String.format("%s/node", datadir));
   }

   public String get(String key) {
      if (keyval.containsKey(key)) return keyval.get(key);
      return null;
   }

   public void set(String key, String val) {
      keyval.put(key, val);
   }

   private boolean firstrun;
   private String datadir;
   private HashMap<String, String> keyval;
}
