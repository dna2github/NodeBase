package seven.drawalive.nodebase;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.io.File;
import java.util.HashMap;

public class NodeBaseApp extends LinearLayout {
   public NodeBaseApp(Context context, NodeBase.AppAction delegate, HashMap<String, Object> env) {
      super(context);
      setOrientation(LinearLayout.VERTICAL);
      _env = env;
      _delegate = delegate;
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
               _readme = Utils.readSmallFile(fentry.getAbsolutePath());
            } else if (name.toLowerCase().compareTo("config") == 0) {
               _config = new NodeBaseAppConfigFile(Utils.readSmallFile(fentry.getAbsolutePath()));
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
      _btnShare = new Button(context);
      _btnShare.setText("Share");
      _btnShare.setEnabled(false);
      subview.addView(_btnShare);
      contents.addView(subview);

      frame.addView(contents);
      addView(frame);
   }

   public void prepareEvents() {
      _btnStart.setOnClickListener(new View.OnClickListener() {
         @Override
         public void onClick(View view) {
            _btnStart.setEnabled(false);
            _btnStop.setEnabled(true);
            _btnShare.setEnabled(true);
            _delegate.signal(
                  new String[]{
                        "start",
                        _appdir.getAbsolutePath(),
                        String.valueOf(_listEntries.getSelectedItem()),
                        _txtParams.getText().toString(),
                        ((EditText)_env.get("txtenv")).getText().toString()
                  });
         }
      });

      _btnStop.setOnClickListener(new View.OnClickListener() {
         @Override
         public void onClick(View view) {
            _btnStart.setEnabled(true);
            _btnStop.setEnabled(false);
            _btnShare.setEnabled(false);
            _delegate.signal(new String[]{"stop"});
         }
      });

      _btnShare.setOnClickListener(new View.OnClickListener() {
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
            Utils.shareInformation(
                  getContext(), "Share", "NodeBase",
                  String.format(
                        "[%s] is running at %s://%s%s%s",
                        name, protocol, Utils.getIPv4(getContext()), port, index
                  ), null);
         }
      });
   }

   public String getAppName() {
      return _appdir.getName();
   }

   private HashMap<String, Object> _env;
   private NodeBase.AppAction _delegate;
   private File _appdir;
   private String[] _appentries;
   private Button _btnStart, _btnStop, _btnShare;
   private Spinner _listEntries;
   private EditText _txtParams;
   private String _readme;
   private NodeBaseAppConfigFile _config;
}
