package net.seven.nodebase.nodebase;

import java.util.ArrayList;
import java.util.HashMap;

import io.flutter.plugin.common.EventChannel;

public class NodeBaseEventHandler implements EventChannel.StreamHandler {
    private HashMap<String, EventChannel.EventSink> sink = new HashMap<>();

    @Override
    public synchronized void onListen(Object name, EventChannel.EventSink events) {
        if (name instanceof String) {
            sink.put((String) name, events);
        }
    }

    @Override
    public synchronized void onCancel(Object name) {
        if (name instanceof String) {
            sink.remove((String) name);
        }
    }

    public synchronized void postMessage(String name, Object message) {
        EventChannel.EventSink ch = sink.get(name);
        if (ch == null) return;
        ch.success(message);
    }
}
