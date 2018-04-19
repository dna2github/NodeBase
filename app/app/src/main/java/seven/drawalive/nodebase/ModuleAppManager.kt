package seven.drawalive.nodebase

import android.content.Context

import java.io.File
import java.io.IOException
import java.nio.charset.Charset

object ModuleAppManager {
    fun js(context: Context): String? {
        val reader = context.resources.openRawResource(R.raw.app_manager)
        try {
            val buf = ByteArray(reader!!.available())
            reader.read(buf)
            return buf.toString(Charset.defaultCharset())
        } catch (e: IOException) {
            return null
        } finally {
            if (reader != null) try {
                reader.close()
            } catch (e: Exception) {
            }

        }
    }

    fun readme(): String {
        return "# NodeBase Application Manager\nrunning: 20180\nparams:  (no params)\n"
    }

    fun config(): String {
        return "name=NodeBase Application Manager\nport=20180\n"
    }

    fun install(context: Context, workdir: String) {
        val appdir = "$workdir/app_manager"
        val dir = File(appdir)
        if (dir.exists()) {
            return
        }
        dir.mkdir()
        Storage.write(js(context)!!, "$appdir/index.js")
        Storage.write(readme(), "$appdir/readme")
        Storage.write(config(), "$appdir/config")
    }
}
