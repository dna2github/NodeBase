package net.seven.nodebase

object StringUtils {
    fun parseArgv(argv: String?): Array<String> {
        var r = arrayOf<String>()
        if (argv == null) return r
        var buf = StringBuffer()
        var state = 0
        var last = ' '
        loop@ for (ch in argv.toCharArray()) {
            when (state) {
                0 -> {
                    if (Character.isSpaceChar(ch)) {
                        if (Character.isSpaceChar(last)) {
                            continue@loop
                        }
                        if (buf.length > 0) {
                            r += String(buf)
                            buf = StringBuffer()
                        }
                        last = ch
                        continue@loop
                    } else if (ch == '"') {
                        state = 1
                    } else if (ch == '\'') {
                        state = 2
                    } else if (ch == '\\') {
                        state += 90
                    }
                    buf.append(ch)
                    last = ch
                }
                1 -> {
                    buf.append(ch)
                    if (ch == '"' && last != '\\') {
                        last = ch
                        state = 0
                        continue@loop
                    }
                    last = ch
                }
                2 -> {
                    buf.append(ch)
                    if (ch == '\'' && last != '\\') {
                        last = ch
                        state = 0
                        continue@loop
                    }
                    last = ch
                }
                90, 91, 92 -> {
                    buf.append(ch)
                    last = ch
                    state -= 90
                }
            }
        }
        if (buf.length > 0) {
            r += String(buf)
        }
        return r
    }
}
