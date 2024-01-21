package net.seven.nodebase.nodebase;

import android.util.Log;

public class Logger {
    public static void i(String tag, String sub, String message) {
        Log.i(tag, String.format("[I] %s :: %s", sub, message));
    }

    public static void w(String tag, String sub, String message) {
        Log.w(tag, String.format("[W] %s :: %s", sub, message));
    }

    public static void e(String tag, String sub, String message) {
        Log.e(tag, String.format("[E] %s :: %s", sub, message));
    }

    public static void d(String tag, String sub, String message) {
        Log.d(tag, String.format("[D] %s :: %s", sub, message));
    }
}
