package seven.drawalive.nodebase

import android.content.Context

import java.util.HashMap


class Configuration(context: Context) {

    private var firstrun: Boolean = false
    private val datadir: String
    private var keyval: HashMap<String, String>? = null

    init {
        datadir = context.applicationInfo.dataDir
        firstrun = false
        load()
    }

    fun load() {
        val infile = String.format("%s/config", datadir)
        keyval = parse(Storage.read(infile))
        if (keyval == null) {
            firstrun = true
            keyval = HashMap()
        }
        if (!keyval!!.containsKey(KEYVAL_NODEBASE_DIR)) {
            keyval!![KEYVAL_NODEBASE_DIR] = "/sdcard/.nodebase"
        }
    }

    fun save() {
        val outfile = String.format("%s/config", datadir)
        val buf = StringBuffer()
        var `val`: String?
        for (key in keyval!!.keys) {
            buf.append(key)
            buf.append('\n')
            buf.append("   ")
            `val` = keyval!![key]
            if (`val` == null) `val` = ""
            if (`val`.indexOf('\n') >= 0) {
                `val` = `val`.replace("\n".toRegex(), "  ")
            }
            buf.append(`val`)
        }
        Storage.write(String(buf), outfile)
    }

    fun dataDir(): String {
        return datadir
    }

    fun workDir(): String {
        return keyval!![KEYVAL_NODEBASE_DIR].orEmpty()
    }

    fun nodeBin(): String {
        return String.format("%s/node/node", datadir)
    }

    fun firstRun(): Boolean {
        return firstrun
    }

    fun prepareEnvironment() {
        Storage.makeDirectory(String.format("%s/node", datadir))
    }

    operator fun get(key: String): String? {
        return if (keyval!!.containsKey(key)) keyval!![key] else null
    }

    operator fun set(key: String, `val`: String) {
        keyval!![key] = `val`
    }

    companion object {

        const val NODE_URL = "https://raw.githubusercontent.com/wiki/dna2github/NodeBase/binary/v0/node"
        const val NPM_URL = "https://raw.githubusercontent.com/wiki/dna2github/NodeBase/binary/v0/npm.zip"
        const val KEYVAL_NODEBASE_DIR = "nodebase_dir"

        fun parse(text: String?): HashMap<String, String>? {
            if (text == null) return null
            val keyval = HashMap<String, String>()
            val lines = text.split("\n".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
            var key: String
            var `val`: String
            var i = 0
            val n = lines.size
            while (i < n) {
                key = lines[i].trim({ it <= ' ' })
                if (key.length == 0) {
                    i++
                    continue
                }
                i++
                if (i >= n) break
                val last = key[key.length - 1]
                var multiple_line = false
                if (last == '+') {
                    key = key.substring(0, key.length - 1).trim({ it <= ' ' })
                    multiple_line = true
                }
                if (key.length == 0) /* after all comments */ break
                if (multiple_line) {
                    `val` = ""
                    for (j in i until n) {
                        `val` += "\n" + lines[j]
                    }
                    i = n
                } else {
                    `val` = lines[i].trim({ it <= ' ' })
                }
                keyval[key] = `val`
                i++
            }
            return keyval
        }
    }
}
