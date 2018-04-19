package seven.drawalive.nodebase

import android.content.Context
import android.support.v7.app.AlertDialog
import android.widget.Toast

object Alarm {
    @JvmOverloads
    fun showMessage(context: Context, text: String, title: String? = null) {
        val builder = AlertDialog.Builder(context)
        builder.setMessage(text)
        if (title != null) builder.setTitle(title)
        builder.create().show()
    }

    fun showToast(context: Context, text: String) {
        Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
    }
}
