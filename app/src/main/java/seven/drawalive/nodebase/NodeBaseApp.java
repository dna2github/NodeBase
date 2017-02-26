package seven.drawalive.nodebase;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;

public class NodeBaseApp extends LinearLayout {
   public NodeBaseApp(Context context, NodeBase.AppAction delegate, File appdir) {
      super(context);
      setOrientation(LinearLayout.VERTICAL);
      _delegate = delegate;
      _appdir = appdir;

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
      TextView label;
      label = new TextView(context);
      label.setText(String.format("\n=============== App: %s ===", _appdir.getName()));
      addView(label);
      label = new TextView(context);
      label.setText(_readme);
      _readme = null; // release memory
      addView(label);
      label = new TextView(context);
      label.setText("Entry");
      addView(label);
      _listEntries = new Spinner(context);
      _listEntries.setAdapter(
            new ArrayAdapter<String>(
                  context, android.R.layout.simple_dropdown_item_1line, _appentries));
      addView(_listEntries);
      label = new TextView(context);
      label.setText("Params");
      addView(label);
      _txtParams = new EditText(context);
      addView(_txtParams);

      LinearLayout subview = new LinearLayout(context);
      subview.setOrientation(LinearLayout.HORIZONTAL);
      _btnStart = new Button(context);
      _btnStart.setText("Start");
      subview.addView(_btnStart);
      _btnStop = new Button(context);
      _btnStop.setText("Stop");
      _btnStop.setEnabled(false);
      subview.addView(_btnStop);
      addView(subview);
   }

   public void prepareEvents() {
      _btnStart.setOnClickListener(new View.OnClickListener() {
         @Override
         public void onClick(View view) {
            _btnStart.setEnabled(false);
            _btnStop.setEnabled(true);
            _delegate.signal(
                  new String[]{
                        "start",
                        _appdir.getAbsolutePath(),
                        String.valueOf(_listEntries.getSelectedItem()),
                        _txtParams.getText().toString()
                  });
         }
      });

      _btnStop.setOnClickListener(new View.OnClickListener() {
         @Override
         public void onClick(View view) {
            _btnStart.setEnabled(true);
            _btnStop.setEnabled(false);
            _delegate.signal(new String[]{"stop"});
         }
      });
   }

   private NodeBase.AppAction _delegate;
   private File _appdir;
   private String[] _appentries;
   private Button _btnStart, _btnStop;
   private Spinner _listEntries;
   private EditText _txtParams;
   private String _readme;
}
