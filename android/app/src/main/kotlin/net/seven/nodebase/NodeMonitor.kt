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

    fun stopService(): Boolean {
        if (state == STATE.RUNNING) node_process!!.destroy()
        state = STATE.DEAD
        val p = node_process!!
        // to make sure the sub process is killed eventually
        android.os.Handler().postDelayed({
            if (p.isAlive()) {
               val klass = p.javaClass
               if (klass.getName().equals("java.lang.UNIXProcess")) {
                   Log.d("NodeMonitor", "force terminate sub process ..")
                   try {
                       var pid = -1;
                       val f = klass.getDeclaredField("pid");
                       f.setAccessible(true);
                       // this try to make sure if getInt throw an error,
                       // `setAccessible(false)` can be executed
                       // so that `pid` is protected after this access
                       try { pid = f.getInt(p); } catch (e: Exception) { }
                       f.setAccessible(false);
                       if (pid > 0) android.os.Process.killProcess(pid);
                       Log.d("NodeMonitor", "force terminating done.")
                   } catch (e: Exception) {
                       Log.d("NodeMonitor", "force terminating failed.")
                   }
               } else {
                   Log.d("NodeMonitor", p.javaClass.getName())
                   Log.d("NodeMonitor", "force terminating not supported.")
               }
            }
        }, 1000)
        return true
    }

    fun restartService(): NodeMonitor {
        stopService()
        val m = NodeMonitor(serviceName, command)
        if (event != null) m.setEvent(event!!)
        return m
    }
}
