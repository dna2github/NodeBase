package net.seven.nodebase


import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log

import java.util.HashMap
import java.util.UUID

class NodeService : Service() {

    override fun onBind(intent: Intent): IBinder? {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        while (intent != null) {
            val argv = intent.getStringArrayExtra(ARGV)
            if (argv.size < 3) break
            val auth_token = argv[0]
            var cmd = argv[1]
            val first = argv[2]
            if (AUTH_TOKEN.compareTo(auth_token) != 0) break
            /*
           command:
             - start
                - start <name> <cmd>
                  - <cmd> = <node> <main.js> ...<argv>
             - restart
                - restart <name> -> restart node app by name
             - stop
                - stop !         -> stop all node apps
                - stop <name>    -> stop node app by name
          */
            when (cmd) {
                "start" -> {
                    if (argv.size >= 4) {
                        cmd = argv[3]
                        startNodeApp(first /* name */, cmd)
                    }
                }
                "restart" -> restartNodeApp(first /* name */)
                "stop" -> if ("!".compareTo(first) == 0) {
                    stopNodeApps()
                } else {
                    stopNodeApp(first /* name */)
                }
            }
            break
        }
        // running until explicitly stop
        return Service.START_STICKY
    }

    override fun onCreate() {
        NodeService.refreshAuthToken()
        stopNodeApps()
    }

    override fun onDestroy() {
        stopNodeApps()
    }

    private fun stopNodeApps() {
        // val n = services.keys.size
        for (name in services.keys.iterator()) {
            stopNodeApp(name.orEmpty())
        }
    }

    private fun stopNodeApp(name: String) {
        if (!services.containsKey(name)) return
        val monitor = services[name]
        monitor!!.stopService()
        services.remove(name)
    }

    private fun restartNodeApp(name: String) {
        if (!services.containsKey(name)) return
        var monitor = services[name]
        stopNodeApp(name)
        monitor = monitor!!.restartService()
        services[name] = monitor
        monitor.start()
    }

    private fun startNodeApp(name: String, cmd: String) {
        stopNodeApp(name)
        Log.d("NodeService:Command", String.format("%s", cmd))
        val exec = StringUtils.parseArgv(cmd)
        val monitor = NodeMonitor(name, exec)
        services[name] = monitor
        monitor.start()
    }

    companion object {
        val ARGV = "NodeService"
        val services = HashMap<String, NodeMonitor>()
        var AUTH_TOKEN = refreshAuthToken()

        fun refreshAuthToken(): String {
            val uuid = UUID.randomUUID()
            return uuid.toString()
        }

        fun checkOutput(cmd: Array<String>): String? {
            try {
                val p = Runtime.getRuntime().exec(cmd)
                p.waitFor()
                val `is` = p.inputStream
                var len = `is`.available()
                var b: ByteArray? = null
                if (len > 0) {
                    b = ByteArray(len)
                    len = `is`.read(b)
                }
                `is`.close()
                return if (b == null) {
                    null
                } else String(b, 0, len)
            } catch (e: Exception) {
                return null
            }

        }


        fun touchService(context: Context, args: Array<String>) {
            Log.i("NodeService:Signal", "Start Service")
            Log.i("NodeService:Signal", String.format("Command - %s", args[1]))
            val intent = Intent(context, NodeService::class.java)
            intent.putExtra(NodeService.ARGV, args)
            context.startService(intent)
        }

        fun stopService(context: Context) {
            Log.i("NodeService:Signal", "Stop Service")
            val intent = Intent(context, NodeService::class.java)
            context.stopService(intent)
        }
    }
}
