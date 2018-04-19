package seven.drawalive.nodebase

import java.util.HashMap

class NodeBaseAppConfigFile(config_text: String) {

    private val config: HashMap<String, HashMap<String, String>>
    private val defaultconfig: HashMap<String, String>

    init {
        config = HashMap()
        defaultconfig = HashMap()
        config["\u0000"] = defaultconfig
        var cur = defaultconfig
        // parse simple ini
        for (line in config_text.split("\n".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()) {
            var xline = line.trim({ it <= ' ' })
            if (xline.length == 0) continue
            if (xline.get(0) == '[' && xline.get(xline.length - 1) == ']') {
                val section = xline.substring(1, xline.length - 1)
                if (config.containsKey(section)) {
                    cur = config[section]!!
                } else {
                    cur = HashMap()
                    config[section] = cur
                }
                continue
            }
            val eqpos = line.indexOf('=')
            if (eqpos < 0) continue
            val key = line.substring(0, eqpos).trim({ it <= ' ' })
            val `val` = line.substring(eqpos + 1).trim({ it <= ' ' })
            cur[key] = `val`
        }
    }

    operator fun get(section: String?, key: String): String? {
        var section = section
        if (section == null) {
            section = "\u0000"
        }
        if (!config.containsKey(section)) {
            return null
        }
        val secmap = config[section]
        return if (secmap!!.containsKey(key)) {
            secmap!!.get(key)
        } else {
            null
        }
    }
}
