package seven.drawalive.nodebase

interface NodeMonitorEvent {
    fun before(cmd: Array<String>)
    fun started(cmd: Array<String>, process: Process)
    fun error(cmd: Array<String>, process: Process)
    fun after(cmd: Array<String>, process: Process)
}
