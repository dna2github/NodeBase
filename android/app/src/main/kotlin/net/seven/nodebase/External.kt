package net.seven.nodebase

import android.content.Context
import android.content.Intent
import android.net.Uri

import java.io.File

object External {
    fun openBrowser(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.action = "android.intent.action.VIEW"
        intent.data = Uri.parse(url)
        context.startActivity(intent)
    }

    fun shareInformation(
            context: Context, title: String,
            label: String, text: String, imgFilePath: String?) {
        val intent = Intent(Intent.ACTION_SEND)
        if (imgFilePath == null || imgFilePath == "") {
            intent.type = "text/plain"
        } else {
            val f = File(imgFilePath)
            if (f.exists() && f.isFile) {
                intent.type = "image/jpg"
                val u = Uri.fromFile(f)
                intent.putExtra(Intent.EXTRA_STREAM, u)
            }
        }
        intent.putExtra(Intent.EXTRA_SUBJECT, label)
        intent.putExtra(Intent.EXTRA_TEXT, text)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(Intent.createChooser(intent, title))
    }
}
