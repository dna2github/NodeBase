package net.seven.nodebase.nodebase;

import java.util.ArrayList;

public class JSONarray extends ArrayList<Object> {
    public JSONarray() { super(); }

    public String toJSONstring() {
        final int n = this.size();
        String[] map = new String[n];
        for (int i = 0; i < n; i++) {
            map[i] = JSONobject.objectToString(this.get(i));
        }
        return String.format("[%s]", String.join(",", map));
    }
}
