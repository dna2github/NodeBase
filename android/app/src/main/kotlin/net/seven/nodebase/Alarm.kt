package net.seven.nodebase

import android.content.Context
import android.widget.Toast

object Alarm {
    fun showToast(context: Context, text: String) {
        Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
    }
}
