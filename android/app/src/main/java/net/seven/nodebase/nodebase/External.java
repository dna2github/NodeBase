package net.seven.nodebase.nodebase;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.util.ArrayList;

public class External {
    public static void openBrowser(Context context, String url) {
        Intent it = new Intent(Intent.ACTION_SEND);
        it.setAction("android.intent.action.VIEW");
        it.setData(Uri.parse(url));
        context.startActivity(it);
    }

    private static String transformAbiToArch(String name) {
        switch(name) {
            case "armeabi-v8a": case "arm64-v8a": return "arm64";
            case "x86_64": return "x64";
            case "x86": return "x86";
            case "armeabi-v7a": case "armeabi": return "arm";
            // mips, mips64
        }
        return "unknown:" + name;
    }
    public static String getArch() {
        ArrayList<String> arch = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("android-");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            for (String one : Build.SUPPORTED_ABIS) {
                arch.add(transformAbiToArch(one));
            }
            sb.append(String.join("|", arch));
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
            // deprecated api
            sb.append(transformAbiToArch(Build.CPU_ABI));
            sb.append('|');
            sb.append(transformAbiToArch(Build.CPU_ABI2));
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.DONUT) {
            // deprecated api
            sb.append(Build.CPU_ABI);
        }
        return sb.toString();
    }

    public static void shareInformation(
            Context context,
            String title,
            String subtitle,
            String text
    ) {
        shareInformation(context, title, subtitle, text, null);
    }
    public static void shareInformation(
            Context context,
            String title,
            String subtitle,
            String text,
            String imgFilePath
    ) {
        Intent it = new Intent(Intent.ACTION_SEND);
        if (imgFilePath == null || imgFilePath.length() == 0) {
            it.setType("text/plain");
        } else {
            File f = new File(imgFilePath);
            if (f.exists() && f.isFile()) {
                it.setType("image/jpg");
                it.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
            } else {
                it.setType("text/plain");
            }
        }
        if (subtitle != null) it.putExtra(Intent.EXTRA_SUBJECT, subtitle);
        if (text != null) it.putExtra(Intent.EXTRA_TEXT, text);
        it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(Intent.createChooser(it, title));
    }
}
