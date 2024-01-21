package net.seven.nodebase.nodebase;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class Command {
    public static String checkOutput(String[] cmd) {
        return checkOutput(cmd, false);
    }
    public static String checkOutput(String[] cmd, boolean joinStderr) {
        try {
            ProcessBuilder build = new ProcessBuilder(cmd);
            if (joinStderr) build.redirectErrorStream(true);
            Process p = build.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }
            p.waitFor();
            reader.close();
            return sb.toString().substring(0, sb.length() - 1);
        } catch(Exception e) {
            Logger.d(
                    "NodeBase",
                    "command",
                    String.format(
                            "%s :: %s",
                            String.join(" ", cmd),
                            e.toString()
                    )
            );
            return null;
        }
    }
}
