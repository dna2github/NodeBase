package seven.drawalive.nodebase;

import java.util.HashMap;

public class NodeBaseAppConfigFile {
   public NodeBaseAppConfigFile(String config_text) {
      config = new HashMap<>();
      defaultconfig = new HashMap<>();
      config.put("\0", defaultconfig);
      HashMap<String, String> cur = defaultconfig;
      // parse simple ini
      for (String line : config_text.split("\n")) {
         line = line.trim();
         if (line.length() == 0) continue;
         if (line.charAt(0) == '[' && line.charAt(line.length()-1) == ']') {
            String section = line.substring(1, line.length()-1);
            if (config.containsKey(section)) {
               cur = config.get(section);
            } else {
               cur = new HashMap<>();
               config.put(section, cur);
            }
            continue;
         }
         int eqpos = line.indexOf('=');
         if (eqpos < 0) continue;
         String key = line.substring(0, eqpos).trim();
         String val = line.substring(eqpos+1).trim();
         cur.put(key, val);
      }
   }

   public String get(String section, String key) {
      if (section == null) {
         section = "\0";
      }
      if (!config.containsKey(section)) {
         return null;
      }
      HashMap<String, String> secmap = config.get(section);
      if (secmap.containsKey(key)) {
         return secmap.get(key);
      } else {
         return null;
      }
   }

   private HashMap<String, HashMap<String, String>> config;
   private HashMap<String, String> defaultconfig;
}
