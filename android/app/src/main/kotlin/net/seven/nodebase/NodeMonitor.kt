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
            BufferedReader reader = new BufferedReader(
                  new InputStreamReader(_process.getInputStream()));
            String line = null;
            while ((line = reader.readLine()) != null) {
               Log.d("NodeMonitor", line);
            }
            Log.d("-----", "==========================");
            reader = new BufferedReader(
                  new InputStreamReader(_process.getErrorStream()));
            while ((line = reader.readLine()) != null) {
               Log.d("NodeMonitor", line);
            }
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
        return true
    }

    fun restartService(): NodeMonitor {
        stopService()
        val m = NodeMonitor(serviceName, command)
        if (event != null) m.setEvent(event!!)
        return m
    }
}
