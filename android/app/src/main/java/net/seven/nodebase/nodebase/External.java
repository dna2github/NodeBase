package net.seven.nodebase.nodebase;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.File;

public class External {
    public static void openBrowser(Context context, String url) {
        Intent it = new Intent(Intent.ACTION_SEND);
        it.setAction("android.intent.action.VIEW");
        it.setData(Uri.parse(url));
        context.startActivity(it);
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
