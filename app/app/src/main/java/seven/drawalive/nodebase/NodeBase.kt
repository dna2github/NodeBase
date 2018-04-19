package seven.drawalive.nodebase

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

import java.io.File
import java.util.ArrayList
import java.util.HashMap


class NodeBase : AppCompatActivity() {

    // state
    private var config: Configuration? = null
    private var _appList: ArrayList<NodeBaseApp>? = null

    // view components
    private var _txtAppRootDir: EditText? = null
    private var _labelIp: Button? = null
    private var _btnRefreshAppList: Button? = null
    private var _txtAppFilter: EditText? = null
    private var _panelAppList: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView(R.layout.activity_node_base);

        config = Configuration(this)
        config!!.prepareEnvironment()

        val view = prepareLayout()
        prepareState()
        prepareEvents()
        Permission.request(this)
        Permission.keepScreen(this, true)

        setContentView(view)
        if (!Storage.exists(config!!.nodeBin())) {
            resetNode()
        }
        if (Storage.exists(config!!.workDir())) {
            refreshAppList()
        }
    }

    override fun onDestroy() {
        Permission.keepScreen(this, false)
        // if want to keep service running in backend
        // comment out this line and add "Stop Service" somewhere
        NodeService.stopService(this)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, 101, Menu.NONE, "NICs")
        menu.add(Menu.NONE, 110, Menu.NONE, "Install Npm")
        menu.add(Menu.NONE, 111, Menu.NONE, "Install App Manager")
        menu.add(Menu.NONE, 120, Menu.NONE, "Node Version")
        menu.add(Menu.NONE, 121, Menu.NONE, "Node Upgrade")
        menu.add(Menu.NONE, 199, Menu.NONE, "Reset")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            101 // Show Network Interfaces
            -> showNicIps()
            110 -> installNpm()
            111 // Install App Manager
            -> installAppManager()
            120 // Show NodeJS Version
            -> showNodeVersion()
            121 // Upgrade NodeJS
            -> copyBinNodeFromNodebaseWorkdir()
            199 // Reset NodeJS
            -> resetNode()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    protected fun prepareState() {
        _appList = ArrayList()
    }

    protected fun prepareLayout(): LinearLayout {
        val view: LinearLayout
        val subview: LinearLayout
        val label: TextView
        val param: LinearLayout.LayoutParams

        view = LinearLayout(this)
        view.orientation = LinearLayout.VERTICAL

        _labelIp = Button(this)
        _labelIp!!.setText(String.format("  Network (%s)", Network.getWifiIpv4(this)))
        _labelIp!!.gravity = Gravity.LEFT
        _labelIp!!.isClickable = false
        _labelIp!!.layoutParams = UserInterface.buttonFillStyle
        UserInterface.themeAppTitleButton(_labelIp!!, false)
        view.addView(_labelIp)

        label = TextView(this)
        label.text = "App Root Dir:"
        view.addView(label)

        subview = LinearLayout(this)
        subview.orientation = LinearLayout.HORIZONTAL
        _txtAppRootDir = EditText(this)
        _txtAppRootDir!!.setText(config!!.workDir())
        param = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        _txtAppRootDir!!.layoutParams = param
        subview.addView(_txtAppRootDir)
        _btnRefreshAppList = Button(this)
        _btnRefreshAppList!!.text = "Refresh"
        subview.addView(_btnRefreshAppList)
        view.addView(subview)

        _txtAppFilter = EditText(this)
        _txtAppFilter!!.setText("")
        _txtAppFilter!!.hint = "Filter app ..."
        _txtAppFilter!!.visibility = View.GONE
        view.addView(_txtAppFilter)

        val scroll = ScrollView(this)
        _panelAppList = LinearLayout(this)
        _panelAppList!!.orientation = LinearLayout.VERTICAL
        scroll.addView(_panelAppList)
        view.addView(scroll)

        return view
    }

    protected fun prepareEvents() {
        _btnRefreshAppList!!.setOnClickListener {
            Log.i("UI:Button", "Refresh app list ...")
            val appdir = _txtAppRootDir!!.text.toString()
            if (appdir.compareTo(config!!.workDir()) != 0) {
                config!![Configuration.KEYVAL_NODEBASE_DIR] = appdir
                config!!.save()
            }
            Storage.makeDirectory(config!!.workDir())
            refreshAppList()
        }

        _txtAppFilter!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                for (app in _appList!!) {
                    if (s.length == 0) {
                        app.visibility = View.VISIBLE
                    } else if (app.appName.indexOf(s.toString()) >= 0) {
                        app.visibility = View.VISIBLE
                    } else {
                        app.visibility = View.GONE
                    }
                }
            }

            override fun afterTextChanged(s: Editable) {}
        })
    }

    protected fun refreshAppList() {
        val dirname = _txtAppRootDir!!.text.toString()
        val approot = File(dirname)
        _panelAppList!!.removeAllViews()
        if (!approot.isDirectory) {
            Alarm.showToast(this, String.format("\"%s\" is not a directory", dirname))
            return
        }
        try {
            _appList!!.clear()
            val files = Storage.listDirectories(dirname)
            for (f in files!!) {
                val name = f.name
                // skip the folders of node_modules and which whose name starts with '.'
                if ("node_modules".compareTo(name) == 0) continue
                if (name.indexOf('.') == 0) continue
                Log.i("UI:AppList", f.absolutePath)
                val env = HashMap<String, Any>()
                env["appdir"] = f
                env["datadir"] = config!!.dataDir()
                val app = NodeBaseApp(this, env)
                _appList!!.add(app)
                _panelAppList!!.addView(app)
            }
            if (_appList!!.size > 0) {
                _txtAppFilter!!.setText("")
                _txtAppFilter!!.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            Log.w("UI:NodeBase", "fail", e)
        }

    }

    private fun copyBinNodeFromNodebaseWorkdir() {
        val dirname = config!!.workDir()
        val upgrade_node_filename = String.format("%s/.bin/node", dirname)
        val f = File(upgrade_node_filename)
        if (!f.exists()) {
            Alarm.showMessage(
                    this,
                    String.format("%s does not exists.", upgrade_node_filename),
                    "Upgrade Failed"
            )
            return
        }
        val nodeBin = config!!.nodeBin()
        if (!Storage.copy(upgrade_node_filename, nodeBin)) {
            Log.e("NodeBase:upgrade_node",
                    "Cannot copy binary file of \"node\"")
        }
        Storage.executablize(nodeBin)
    }

    private fun showNodeVersion() {
        val version = NodeService.checkOutput(arrayOf(String.format("%s/node/node", config!!.dataDir()), "--version"))
        var text: String? = null
        if (version == null) {
            text = "NodeJS: (not found)"
        } else {
            text = String.format("NodeJS: %s", version)
        }
        Alarm.showMessage(this, text!!, "Node Version")
    }

    private fun showNicIps() {
        val name_ip = Network.nicIps
        val nic_list = StringBuffer()
        for (name in name_ip.keys) {
            nic_list.append(name)
            nic_list.append(':')
            for (ip in name_ip[name]!!.iterator()) {
                nic_list.append(' ')
                nic_list.append('[')
                nic_list.append(ip)
                nic_list.append(']')
            }
            nic_list.append('\n')
        }
        val text = String(nic_list)
        Alarm.showMessage(this, text, "NetworkInterface(s)")
    }

    private fun resetNode() {
        val workdir = config!!.workDir()
        val workdir_bin = String.format("%s/.bin", workdir)
        Storage.makeDirectory(workdir_bin)
        val upgrade_node_filename = String.format("%s/node", workdir_bin)
        Storage.unlink(upgrade_node_filename)
        Downloader(this, Runnable { copyBinNodeFromNodebaseWorkdir() }).act("Downlaod NodeJS", Configuration.NODE_URL, upgrade_node_filename)
    }

    private fun installAppManager() {
        val workdir = config!!.workDir()
        ModuleAppManager.install(this, workdir)
        Alarm.showToast(this, "successful")
    }

    private fun installNpm() {
        val workdir = config!!.workDir()
        if (Storage.exists(String.format("%s/node_modules/npm", workdir))) return
        val workdir_node_modules = String.format("%s/node_modules", workdir)
        Storage.makeDirectory(workdir_node_modules)
        val upgrade_npm_filename = String.format("%s/npm.zip", workdir_node_modules)
        Storage.unlink(upgrade_npm_filename)
        Downloader(this, Runnable {
            ModuleNpm.InstallNpmFromZip(upgrade_npm_filename, workdir_node_modules)
            Storage.unlink(upgrade_npm_filename)
        }).act("Downlaod Npm", Configuration.NPM_URL, upgrade_npm_filename)
    }
}
