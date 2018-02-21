package seven.drawalive.nodebase;

import android.content.Context;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.io.File;
import java.util.HashMap;

public class NodeBaseApp extends LinearLayout implements NodeService.NodeMonitorEvent {
   public NodeBaseApp(Context context, HashMap<String, Object> env) {
      super(context);
      setOrientation(LinearLayout.VERTICAL);
      _context = context;
      _env = env;
      _appdir = (File)env.get("appdir");

      collectAppInformation();
      prepareLayout();
      prepareEvents();
   }

   public void collectAppInformation() {
      try {
         // get all app entries
         // e.g. /sdcard/.nodebase/app1/{entry1.js,entry2.js,...}
         File[] fentries = _appdir.listFiles();
         String[] entries = new String[fentries.length];
         int count = 0;
         _readme = "(This is a NodeBase app)";
         for (int i = fentries.length - 1; i >= 0; i--) {
            File fentry = fentries[i];
            entries[i] = null;
            if (!fentry.isFile()) continue;
            String name = fentry.getName();
            if (name.endsWith(".js")) {
               entries[i] = name;
               count ++;
            } else if (name.toLowerCase().compareTo("readme") == 0) {
               _readme = Storage.read(fentry.getAbsolutePath());
            } else if (name.toLowerCase().compareTo("config") == 0) {
               _config = new NodeBaseAppConfigFile(Storage.read(fentry.getAbsolutePath()));
            }
         }

         _appentries = new String[count];
         for (int i = entries.length - 1; i >= 0; i--) {
            if (entries[i] == null) continue;
            count --;
            _appentries[count] = entries[i];
         }
      } catch (Exception e) {
         Log.w("UI:NodeBaseApp", "fail", e);
      }
   }

   public void prepareLayout() {
      Context context = getContext();
      LinearLayout frame = new LinearLayout(context);
      frame.setOrientation(LinearLayout.HORIZONTAL);

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

      LinearLayout contents = new LinearLayout(context);
      contents.setOrientation(LinearLayout.VERTICAL);

      TextView label;
      label = new TextView(context);
      label.setText(String.format("\nApp: %s", _appdir.getName()));
      label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
      contents.addView(label);
      label = new TextView(context);
      label.setText(_readme);
      _readme = null; // release memory
      contents.addView(label);

      TableLayout tbl = new TableLayout(context);
      TableRow tbl_r_t = null;
      tbl_r_t = new TableRow(context);
      label = new TextView(context);
      label.setText("Entry");
      tbl_r_t.addView(label);
      label = new TextView(context);
      label.setText("Params");
      tbl_r_t.addView(label);
      tbl.addView(tbl_r_t);
      tbl_r_t = new TableRow(context);
      _listEntries = new Spinner(context);
      _listEntries.setAdapter(
            new ArrayAdapter<String>(
                  context, android.R.layout.simple_spinner_dropdown_item, _appentries));
      tbl_r_t.addView(_listEntries);
      _txtParams = new EditText(context);
      tbl_r_t.addView(_txtParams);
      tbl.addView(tbl_r_t);
      tbl.setStretchAllColumns(true);
      contents.addView(tbl);


      LinearLayout subview = new LinearLayout(context);
      subview.setOrientation(LinearLayout.HORIZONTAL);
      _btnStart = new Button(context);
      _btnStart.setText("Start");
      subview.addView(_btnStart);
      _btnStop = new Button(context);
      _btnStop.setText("Stop");
      _btnStop.setEnabled(false);
      subview.addView(_btnStop);
      _btnOpen = new Button(context);
      _btnOpen.setText("Open");
      _btnOpen.setEnabled(false);
      subview.addView(_btnOpen);
      _btnShare = new Button(context);
      _btnShare.setText("Share");
      _btnShare.setEnabled(false);
      subview.addView(_btnShare);
      contents.addView(subview);

      frame.addView(contents);
      addView(frame);
   }

   public void prepareEvents() {
      _btnStart.setOnClickListener(new OnClickListener() {
         @Override
         public void onClick(View view) {
            _btnStart.setEnabled(false);
            _btnStop.setEnabled(true);
            _btnOpen.setEnabled(true);
            _btnShare.setEnabled(true);
            NodeService.touchService(
                  _context,
                  new String[]{
                        NodeService.AUTH_TOKEN,
                        "start",
                        _appdir.getName(),
                        String.format(
                              "%s/node/node %s/%s %s",
                              (String)_env.get("datadir"),
                              _appdir.getAbsolutePath(),
                              String.valueOf(_listEntries.getSelectedItem()),
                              _txtParams.getText().toString()
                        )
                  });
         }
      });

      _btnStop.setOnClickListener(new OnClickListener() {
         @Override
         public void onClick(View view) {
            _btnStart.setEnabled(true);
            _btnStop.setEnabled(false);
            _btnOpen.setEnabled(false);
            _btnShare.setEnabled(false);
            NodeService.touchService(_context, new String[]{
                  NodeService.AUTH_TOKEN,
                  "stop", _appdir.getName()
            });
         }
      });

      _btnOpen.setOnClickListener(new OnClickListener() {
         @Override
         public void onClick(View v) {
            String name = null, protocol = null, port = null, index = null;
            if (_config != null) {
               name = _config.get(null, "name");
               port = _config.get(null, "port");
               protocol = _config.get(null, "protocol");
               index = _config.get(null, "index");
            }
            if (name == null) name = "NodeBase Service";
            if (port == null) port = ""; else port = ":" + port;
            if (protocol == null) protocol = "http";
            if (index == null) index = "";
            String url = String.format(
                  "%s://%s%s%s", protocol, Network.getWifiIpv4(getContext()), port, index
            );
            External.openBrowser(getContext(), url);
         }
      });

      _btnShare.setOnClickListener(new OnClickListener() {
         @Override
         public void onClick(View view) {
            String name = null, protocol = null, port = null, index = null;
            if (_config != null) {
               name = _config.get(null, "name");
               port = _config.get(null, "port");
               protocol = _config.get(null, "protocol");
               index = _config.get(null, "index");
            }
            if (name == null) name = "NodeBase Service";
            if (port == null) port = ""; else port = ":" + port;
            if (protocol == null) protocol = "http";
            if (index == null) index = "";
            External.shareInformation(
                  getContext(), "Share", "NodeBase",
                  String.format(
                        "[%s] is running at %s://%s%s%s",
                        name, protocol, Network.getWifiIpv4(getContext()), port, index
                  ), null);
         }
      });
   }

   public String getAppName() {
      return _appdir.getName();
   }

   @Override
   public void before(String[] cmd) {
      _btnStart.setEnabled(false);
      _btnStop.setEnabled(false);
      _btnOpen.setEnabled(false);
      _btnShare.setEnabled(false);
   }

   @Override
   public void started(String[] cmd, Process process) {
      _btnStart.setEnabled(false);
      _btnStop.setEnabled(true);
      _btnOpen.setEnabled(true);
      _btnShare.setEnabled(true);
   }

   @Override
   public void error(String[] cmd, Process process) {
   }

   @Override
   public void after(String[] cmd, Process process) {
      _btnStart.setEnabled(true);
      _btnStop.setEnabled(false);
      _btnOpen.setEnabled(false);
      _btnShare.setEnabled(false);
   }

   private HashMap<String, Object> _env;
   private File _appdir;
   private String[] _appentries;
   private Button _btnStart, _btnStop, _btnOpen, _btnShare;
   private Spinner _listEntries;
   private EditText _txtParams;
   private String _readme;
   private NodeBaseAppConfigFile _config;
   private Context _context;
}
