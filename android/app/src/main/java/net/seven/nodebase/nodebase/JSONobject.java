package net.seven.nodebase.nodebase;

import java.util.HashMap;
import java.util.Map;

public class JSONobject extends HashMap<String, Object> {

    public JSONobject() { super(); }

    public String toJSONstring() {
        final int n = this.size();
        String[] map = new String[n];
        int i = 0;
        for (Map.Entry<String, Object> one : this.entrySet()) {
            String k = one.getKey();
            Object v = one.getValue();
            map[i] = String.format("\"%s\":\"%s\"", escapeString(k), objectToString(v));
            i ++;
        }
        return String.format("{%s}", String.join(",", map));
    }

    public static String objectToString(Object obj) {
        if (obj == null) return null;
        if (obj instanceof JSONobject) return ((JSONobject) obj).toJSONstring();
        if (obj instanceof JSONarray) return ((JSONarray) obj).toJSONstring();
        if (obj instanceof Boolean) return ((boolean)obj) ? "true" : "false";
        if (obj instanceof Integer) return String.valueOf(obj);
        if (obj instanceof Long) return String.valueOf(obj);
        if (obj instanceof Float) return String.valueOf(obj);
        if (obj instanceof Double) return String.valueOf(obj);
        return String.format("\"%s\"", escapeString(obj.toString()));
    }

    public static String escapeString(String str) {
        return str
                .replaceAll("\\\\", "\\\\")
                .replaceAll("\\t", "\\t")
                .replaceAll("\\r", "\\r")
                .replaceAll("\"", "\\\"")
                .replaceAll("\\n", "\\n");
    }
}
