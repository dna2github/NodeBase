package seven.drawalive.nodebase

import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView

import java.io.File
import java.util.HashMap

class NodeBaseApp(context: Context, private val _env: HashMap<String, Any>) : LinearLayout(context), NodeMonitorEvent {

    val appName: String
        get() = _appdir.name
    private val _appdir: File
    private var _appentries: Array<String?>? = null
    private var _panelDetails: LinearLayout? = null
    private var _btnTitle: Button? = null
    private var _btnStart: Button? = null
    private var _btnStop: Button? = null
    private var _btnOpen: Button? = null
    private var _btnShare: Button? = null
    private var _listEntries: Spinner? = null
    private var _txtParams: EditText? = null
    private var _readme: String? = null
    private var _config: NodeBaseAppConfigFile? = null

    init {
        orientation = LinearLayout.VERTICAL
        _appdir = _env["appdir"] as File

        collectAppInformation()
        prepareLayout()
        prepareEvents()
    }

    fun collectAppInformation() {
        try {
            // get all app entries
            // e.g. /sdcard/.nodebase/app1/{entry1.js,entry2.js,...}
            val fentries = _appdir.listFiles()
            val entries = arrayOfNulls<String>(fentries.size)
            var count = 0
            _readme = "(This is a NodeBase app)"
            for (i in fentries.indices.reversed()) {
                val fentry = fentries[i]
                entries[i] = null
                if (!fentry.isFile) continue
                val name = fentry.name
                if (name.endsWith(".js")) {
                    entries[i] = name
                    count++
                } else if (name.toLowerCase().compareTo("readme") == 0) {
                    _readme = Storage.read(fentry.absolutePath)
                } else if (name.toLowerCase().compareTo("config") == 0) {
                    _config = NodeBaseAppConfigFile(Storage.read(fentry.absolutePath)!!)
                }
            }

            _appentries = arrayOfNulls(count)
            for (i in entries.indices.reversed()) {
                if (entries[i] == null) continue
                count--
                _appentries!![count] = entries[i]
            }
        } catch (e: Exception) {
            Log.w("UI:NodeBaseApp", "fail", e)
        }

    }

    fun prepareLayout() {
        val context = context
        val frame = LinearLayout(context)
        frame.orientation = LinearLayout.HORIZONTAL

        /*ImageView image = new ImageView(context);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(64, 64);
      params.setMargins(1, 1, 1, 1);
      image.setLayoutParams(params);
      image.setMaxHeight(64);
      image.setMaxWidth(64);
      image.setMinimumHeight(64);
      image.setMinimumWidth(64);
      try {
         File imgfile = new File(_appdir.getAbsolutePath().concat("/icon.png"));
         if (imgfile.exists()) {
            Bitmap bmp = BitmapFactory.decodeFile(imgfile.getAbsolutePath());
            image.setImageBitmap(bmp);
         } else {
            image.setBackgroundResource(R.drawable.default_icon);
         }
      } catch (Exception e) {
      }
      frame.addView(image);*/

        val param: LinearLayout.LayoutParams
        val contents = LinearLayout(context)
        contents.orientation = LinearLayout.VERTICAL

        _btnTitle = Button(context)
        _btnTitle!!.setText(String.format("  App : %s", appName))
        _btnTitle!!.gravity = Gravity.LEFT
        _btnTitle!!.setAllCaps(false)
        _btnTitle!!.layoutParams = UserInterface.buttonLeftStyle
        UserInterface.themeAppTitleButton(_btnTitle!!, false)
        contents.addView(_btnTitle)

        _panelDetails = LinearLayout(context)
        _panelDetails!!.orientation = LinearLayout.VERTICAL
        _panelDetails!!.visibility = View.GONE
        var label: TextView
        label = TextView(context)
        label.text = _readme
        _readme = null // release memory
        _panelDetails!!.addView(label)

        val tbl = TableLayout(context)
        var tbl_r_t: TableRow? = null
        tbl_r_t = TableRow(context)
        label = TextView(context)
        label.text = "Entry"
        tbl_r_t.addView(label)
        label = TextView(context)
        label.text = "Params"
        tbl_r_t.addView(label)
        tbl.addView(tbl_r_t)
        tbl_r_t = TableRow(context)
        _listEntries = Spinner(context)
        _listEntries!!.adapter = ArrayAdapter(
                context, android.R.layout.simple_spinner_dropdown_item, _appentries!!)
        tbl_r_t.addView(_listEntries)
        _txtParams = EditText(context)
        tbl_r_t.addView(_txtParams)
        tbl.addView(tbl_r_t)
        tbl.isStretchAllColumns = true
        _panelDetails!!.addView(tbl)


        val subview = LinearLayout(context)
        subview.orientation = LinearLayout.HORIZONTAL
        _btnStart = Button(context)
        _btnStart!!.text = "Start"
        subview.addView(_btnStart)
        _btnStop = Button(context)
        _btnStop!!.text = "Stop"
        _btnStop!!.isEnabled = false
        subview.addView(_btnStop)
        _btnOpen = Button(context)
        _btnOpen!!.text = "Open"
        _btnOpen!!.isEnabled = false
        subview.addView(_btnOpen)
        _btnShare = Button(context)
        _btnShare!!.text = "Share"
        _btnShare!!.isEnabled = false
        subview.addView(_btnShare)
        _panelDetails!!.addView(subview)

        param = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        )
        param.setMargins(0, 5, 0, 0)
        contents.layoutParams = param
        contents.addView(_panelDetails)

        frame.addView(contents)
        addView(frame)
    }

    fun prepareEvents() {
        _btnTitle!!.setOnClickListener {
            if (_panelDetails!!.visibility == View.GONE) {
                _panelDetails!!.visibility = View.VISIBLE
            } else {
                _panelDetails!!.visibility = View.GONE
            }
        }

        _btnStart!!.setOnClickListener {
            val appname = appName
            _btnStart!!.isEnabled = false
            _btnStop!!.isEnabled = true
            _btnOpen!!.isEnabled = true
            _btnShare!!.isEnabled = true
            Thread(Runnable {
                val appname = appName
                val timestamp = System.currentTimeMillis()
                while (System.currentTimeMillis() - timestamp < 3000 /* 3s timeout */) {
                    if (NodeService.services.containsKey(appname)) {
                        val monitor = NodeService.services[appname]
                        if (monitor!!.isDead) {
                            // not guarantee but give `after` get chance to run
                            // if want to guarantee, `synchronized` isDead
                            this@NodeBaseApp.after(monitor!!.command, null!!)
                        } else {
                            monitor!!.setEvent(this@NodeBaseApp)
                        }
                        break
                    }
                }
            }).start()
            NodeService.touchService(
                    context,
                    arrayOf(NodeService.AUTH_TOKEN, "start", appname, String.format(
                            "%s/node/node %s/%s %s",
                            _env["datadir"].toString(),
                            _appdir.absolutePath,
                            _listEntries!!.selectedItem.toString(),
                            _txtParams!!.text.toString()
                    )))
        }

        _btnStop!!.setOnClickListener {
            _btnStart!!.isEnabled = true
            _btnStop!!.isEnabled = false
            _btnOpen!!.isEnabled = false
            _btnShare!!.isEnabled = false
            NodeService.touchService(context, arrayOf(NodeService.AUTH_TOKEN, "stop", appName))
        }

        _btnOpen!!.setOnClickListener {
            val app_url = String.format(
                    generateAppUrlTemplate(),
                    Network.getWifiIpv4(context)
            )
            External.openBrowser(context, app_url)
        }

        _btnShare!!.setOnClickListener {
            val name = generateAppTitle()
            val app_url = String.format(
                    generateAppUrlTemplate(),
                    Network.getWifiIpv4(context)
            )
            External.shareInformation(
                    context, "Share", "NodeBase",
                    String.format("[%s] is running at %s", name, app_url), null
            )
        }
    }

    private fun generateAppUrlTemplate(): String {
        var protocol: String? = null
        var port: String? = null
        var index: String? = null
        if (_config != null) {
            port = _config!![null, "port"]
            protocol = _config!![null, "protocol"]
            index = _config!![null, "index"]
        }
        if (port == null) port = "" else port = ":$port"
        if (protocol == null) protocol = "http"
        if (index == null) index = ""
        return protocol + "://%s" + String.format("%s%s", port, index)
    }

    private fun generateAppTitle(): String {
        var name: String? = null
        if (_config != null) {
            name = _config!![null, "name"]
        }
        if (name == null) name = "NodeBase Service"
        return name
    }

    override fun before(cmd: Array<String>) {
        UserInterface.run(Runnable {
            _btnStart!!.isEnabled = false
            _btnStop!!.isEnabled = false
            _btnOpen!!.isEnabled = false
            _btnShare!!.isEnabled = false
        })
    }

    override fun started(cmd: Array<String>, process: Process) {
        UserInterface.run(Runnable {
            _btnStart!!.isEnabled = false
            _btnStop!!.isEnabled = true
            _btnOpen!!.isEnabled = true
            _btnShare!!.isEnabled = true
            UserInterface.themeAppTitleButton(_btnTitle!!, true)
        })
    }

    override fun error(cmd: Array<String>, process: Process) {}

    override fun after(cmd: Array<String>, process: Process) {
        UserInterface.run(Runnable {
            _btnStart!!.isEnabled = true
            _btnStop!!.isEnabled = false
            _btnOpen!!.isEnabled = false
            _btnShare!!.isEnabled = false
            UserInterface.themeAppTitleButton(_btnTitle!!, false)
            Alarm.showToast(
                    this@NodeBaseApp.context,
                    String.format("\"%s\" stopped", appName)
            )
        })
    }
}
