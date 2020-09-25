package net.seven.nodebase

import android.util.Log

import java.io.IOException

class NodeMonitor(val serviceName: String, val command: Array<String>) : Thread() {

    val isRunning: Boolean
        get() = state == STATE.RUNNING

    val isReady: Boolean
        get() = state == STATE.READY

    val isDead: Boolean
        get() = state == STATE.DEAD

    private var state: STATE? = null
    private var node_process: Process? = null
    private var event: NodeMonitorEvent? = null

    enum class STATE {
        BORN, READY, RUNNING, DEAD
    }

    init {
        state = STATE.BORN
        event = null
    }

    fun setEvent(event: NodeMonitorEvent): NodeMonitor {
        this.event = event
        return this
    }

    override fun run() {
        try {
            state = STATE.READY
            if (event != null) event!!.before(command)
            Log.i("NodeService:NodeMonitor", String.format("node process starting - %s", *command))

            node_process = Runtime.getRuntime().exec(command)
            state = STATE.RUNNING
            if (event != null) event!!.started(command, node_process!!)
            Log.i("NodeService:NodeMonitor", "node process running ...")
            node_process!!.waitFor()
            /*
            for (x in command) { System.out.println("  - $x"); }
            node_process!!.inputStream.bufferedReader().use { Log.d("NodeMonitor", it.readText()) }
            Log.d("-----", "==========================");
            node_process!!.errorStream.bufferedReader().use { Log.d("NodeMonitor", it.readText()) }
            */
        } catch (e: IOException) {
            Log.e("NodeService:NodeMonitor", "node process error", e)
            node_process = null
            if (event != null) event!!.error(command, null!!)
        } catch (e: InterruptedException) {
            Log.e("NodeService:NodeMonitor", "node process error", e)
            if (event != null) event!!.error(command, node_process!!)
        } finally {
            state = STATE.DEAD
            if (event != null) event!!.after(command, node_process!!)
            Log.i("NodeService:NodeMonitor", "node process stopped ...")
        }
    }

    fun pidService(): Int {
        val p = node_process!!
        if (p == null) return -1
        if (!p.isAlive()) return -1
        val klass = p.javaClass
        if ("java.lang.UNIXProcess".equals(klass.getName())) {
            try {
                var pid = -1
                val f = klass.getDeclaredField("pid");
                f.setAccessible(true);
                // this try to make sure if getInt throw an error,
                // `setAccessible(false)` can be executed
                // so that `pid` is protected after this access
                try { pid = f.getInt(p); } catch (e: Exception) { }
                f.setAccessible(false);
                return pid
            } catch (e: Exception) { }
        }
        return -1
    }

    fun childrenProcesses(pid: Int): Array<Int> {
        var children = arrayOf<Int>()
        val output = NodeService.checkOutput(arrayOf<String>("/system/bin/ps", "-o", "pid=", "--ppid", pid.toString()))
        if (output == "") return children
        val lines = output!!.split("\n")
        lines.forEach {
            if (it != "") {
                try {
                    children += it.toInt()
                } catch(e: Exception) {}
            }
        }
        return children
    }

    fun stopService(): Boolean {
        val pid = pidService();
        if (pid > 0) {
           // XXX: we only make sure one level children processes can be cleaned up
           //      for example `go run test.go` -> `test`
           //      we reap `test` first and then kill `go run test.go`
           //      we do not guarantee `test` children are killed
           //      another example, if we use `sh -c "go run test.go"` -> `go run test.go` -> `test`
           //      when kill, we merely kill `go` and `sh` but no `test`
           Log.d("NodeMonitor", NodeService.checkOutput(arrayOf<String>("/system/bin/ps", "-ef")))
           val children = childrenProcesses(pid)
           children.forEach {
               if (it > 0) {
                   Log.d("NodeMonitor", String.format("kill %d | parent=%d", it, pid))
                   android.os.Process.killProcess(it)
               }
           }
        }
        if (state == STATE.RUNNING) node_process!!.destroy()
        return true
    }

    fun restartService(): NodeMonitor {
        stopService()
        val m = NodeMonitor(serviceName, command)
        if (event != null) m.setEvent(event!!)
        return m
    }
}
